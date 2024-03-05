/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.internal.TracesAdapter;

/**
 * A component that provides storage interfaces used for spans and aggregations. Implementations are
 * free to provide other interfaces, but the ones declared here must be supported.
 *
 * @see InMemoryStorage
 */
public abstract class StorageComponent extends Component {

  public Traces traces() {
    return new TracesAdapter(spanStore()); // delegates to deprecated methods.
  }

  public abstract SpanStore spanStore();

  public AutocompleteTags autocompleteTags() { // returns default to not break compat
    return new AutocompleteTags() {
      @Override public Call<List<String>> getKeys() {
        return Call.emptyList();
      }

      @Override public Call<List<String>> getValues(String key) {
        return Call.emptyList();
      }

      @Override public String toString() {
        return "EmptyAutocompleteTags{}";
      }
    };
  }

  public ServiceAndSpanNames serviceAndSpanNames() { // delegates to deprecated methods.
    final SpanStore delegate = spanStore();
    return new ServiceAndSpanNames() {
      @Override public Call<List<String>> getServiceNames() {
        return delegate.getServiceNames();
      }

      @Override public Call<List<String>> getRemoteServiceNames(String serviceName) {
        return Call.emptyList(); // incorrect for not yet ported 3rd party storage components.
      }

      @Override public Call<List<String>> getSpanNames(String serviceName) {
        return delegate.getSpanNames(serviceName);
      }

      @Override public String toString() {
        return "ServiceAndSpanNames{" + delegate + "}";
      }
    };
  }

  public abstract SpanConsumer spanConsumer();

  /**
   * A storage request failed and was dropped due to a limit, resource unavailability, or a timeout.
   * Implementations of throttling can use this signal to differentiate between failures, for
   * example to reduce traffic.
   *
   * <p>Callers of this method will submit an exception raised by {@link Call#execute()} or on the
   * error callback of {@link Call#enqueue(Callback)}.
   *
   * <p>By default, this returns true if the input is a {@link RejectedExecutionException}. When
   * originating exceptions, use this type to indicate a load related failure.
   *
   * <p>It is generally preferred to specialize this method to handle relevant exceptions for the
   * particular storage rather than wrapping them in {@link RejectedExecutionException} at call
   * sites. Extra wrapping can make errors harder to read, for example, by making it harder to
   * "google" a solution for a well known error message for the storage client, instead thinking the
   * error is in Zipkin code itself.
   *
   * <h3>See also</h3>
   * <p>While implementation is flexible, one known use is <a href="https://github.com/Netflix/concurrency-limits">Netflix
   * concurrency limits</a>
   */
  public boolean isOverCapacity(Throwable e) {
    return e instanceof RejectedExecutionException;
  }

  public static abstract class Builder {

    /**
     * Zipkin supports 64 and 128-bit trace identifiers, typically serialized as 16 or 32 character
     * hex strings. When false, this setting only considers the low 64-bits (right-most 16
     * characters) of a trace ID when grouping or retrieving traces. This should be set to false
     * while some applications issue 128-bit trace IDs and while other truncate them to 64-bit. If
     * 128-bit trace IDs are not in use, this setting is not required.
     *
     * <h3>Details</h3>
     *
     * <p>Zipkin historically had 64-bit {@link Span#traceId() trace IDs}, but it now supports 128-
     * bit trace IDs via 32-character hex representation. While instrumentation update to propagate
     * 128-bit IDs, it can be ambiguous whether a 64-bit trace ID was sent intentionally, or as an
     * accident of truncation. This setting allows Zipkin to be usable until application
     * instrumentation are upgraded to support 128-bit trace IDs.
     *
     * <p>Here are a few trace IDs the help explain this setting.
     *
     * <pre><ul>
     *   <li>Trace ID A: 463ac35c9f6413ad48485a3953bb6124</li>
     *   <li>Trace ID B: 48485a3953bb6124</li>
     *   <li>Trace ID C: 463ac35c9f6413adf1a48a8cff464e0e</li>
     *   <li>Trace ID D: 463ac35c9f6413ad</li>
     * </ul></pre>
     *
     * <p>In the above example, Trace ID A and Trace ID B might mean they are in the same trace,
     * since the lower-64 bits of the IDs are the same. This could happen if a server A created the
     * trace and propagated it to server B which ran an older tracing library. Server B could have
     * truncated the trace ID to lower-64 bits. When {@code strictTraceId == false}, spans matching
     * either trace ID A or B would be returned in the same trace when searching by ID A or B. Spans
     * with trace ID C or D wouldn't be when searching by ID A or B because trace IDs C and D don't
     * share lower 64-bits (right-most 16 characters) with trace IDs A or B.
     *
     * <p>It is also possible that all servers are capable of handling 128-bit trace identifiers,
     * but are configured to only send 64-bit ones. In this case, if {@code strictTraceId == false}
     * trace ID A and B would clash and be put into the same trace, causing confusion. Moreover,
     * there is overhead associated with indexing spans both by 64 and 128-bit trace IDs. When a
     * site has finished upgrading to 128-bit trace IDs, they should enable this setting.
     *
     * <p>See https://github.com/openzipkin/b3-propagation/issues/6 for the status of
     * known open source libraries on 128-bit trace identifiers.
     */
    public abstract Builder strictTraceId(boolean strictTraceId);

    /**
     * False is an attempt to disable indexing, leaving only {@link StorageComponent#traces()}
     * supported. For example, query requests will be disabled.
     * <p>
     * The use case is typically to support 100% sampled data, or when traces are searched using
     * alternative means such as a logging index.
     *
     * <p>Refer to implementation docs for the impact of this parameter. Operations that use
     * indexes should return empty as opposed to throwing an exception.
     */
    public abstract Builder searchEnabled(boolean searchEnabled);

    /**
     * Autocomplete is used by the UI to suggest getValues for site-specific tags, such as
     * environment names. The getKeys here would appear in {@link Span#tags() span tags}. Good
     * choices for autocomplete are limited in cardinality for the same reasons as service and span
     * names.
     * <p>
     * For example, "http.url" would be a bad choice for autocomplete, not just because it isn't
     * site-specific (such as environment would be), but also as there are unlimited getValues due
     * to factors such as unique ids in the path.
     *
     * @param keys controls the span values stored for auto-complete.
     */
    public Builder autocompleteKeys(List<String> keys) { // not abstract as added later
      Logger.getLogger(getClass().getName()).info("autocompleteKeys not yet supported");
      return this;
    }

    /** How long in milliseconds to suppress calls to write the same autocomplete key/value pair. */
    public Builder autocompleteTtl(int autocompleteTtl) { // not abstract as added later
      Logger.getLogger(getClass().getName()).info("autocompleteTtl not yet supported");
      return this;
    }

    /** How many autocomplete key/value pairs to suppress at a time. */
    public Builder autocompleteCardinality(
      int autocompleteCardinality) { // not abstract as added later
      Logger.getLogger(getClass().getName()).info("autocompleteCardinality not yet supported");
      return this;
    }

    public abstract StorageComponent build();
  }
}
