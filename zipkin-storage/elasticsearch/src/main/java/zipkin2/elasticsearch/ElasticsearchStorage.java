/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.ResponseTimeoutException;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpMethod;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.Internal;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.HttpCall.BodyConverter;
import zipkin2.internal.Nullable;
import zipkin2.internal.Platform;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

import static zipkin2.elasticsearch.ElasticsearchAutocompleteTags.AUTOCOMPLETE;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.DEPENDENCY;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;
import static zipkin2.elasticsearch.EnsureIndexTemplate.ensureIndexTemplate;
import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

@AutoValue
public abstract class ElasticsearchStorage extends zipkin2.storage.StorageComponent {

  /**
   * This defers creation of an {@link HttpClient}. This is needed because routinely, I/O occurs in
   * constructors and this can delay or cause startup to crash. For example, an underlying {@link
   * EndpointGroup} could be delayed due to DNS, implicit api calls or health checks.
   */
  public interface LazyHttpClient extends Supplier<HttpClient>, Closeable {
    /**
     * Lazily creates an instance of the http client configured to the correct elasticsearch host or
     * cluster. The same value should always be returned.
     */
    @Override HttpClient get();

    @Override default void close() {
    }

    /** This should return the initial endpoints in a single-string without resolving them. */
    @Override String toString();
  }

  /** The lazy http client supplier will be closed on {@link #close()} */
  public static Builder newBuilder(LazyHttpClient lazyHttpClient) {
    return new $AutoValue_ElasticsearchStorage.Builder()
      .lazyHttpClient(lazyHttpClient)
      .strictTraceId(true)
      .searchEnabled(true)
      .index("zipkin")
      .dateSeparator('-')
      .indexShards(5)
      .indexReplicas(1)
      .namesLookback(86400000)
      .flushOnWrites(false)
      .autocompleteKeys(Collections.emptyList())
      .autocompleteTtl((int) TimeUnit.HOURS.toMillis(1))
      .autocompleteCardinality(5 * 4000); // Ex. 5 site tags with cardinality 4000 each
  }

  abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder extends StorageComponent.Builder {

    /**
     * Only valid when the destination is Elasticsearch 5.x. Indicates the ingest pipeline used
     * before spans are indexed. No default.
     *
     * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html
     */
    public abstract Builder pipeline(String pipeline);

    /**
     * Only return span and service names where all {@link zipkin2.Span#timestamp()} are at or after
     * (now - lookback) in milliseconds. Defaults to 1 day (86400000).
     */
    public abstract Builder namesLookback(int namesLookback);

    /**
     * Internal and visible only for testing.
     *
     * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-refresh.html
     */
    public abstract Builder flushOnWrites(boolean flushOnWrites);

    /** The index prefix to use when generating daily index names. Defaults to zipkin. */
    public final Builder index(String index) {
      indexNameFormatterBuilder().index(index);
      return this;
    }

    /**
     * The date separator to use when generating daily index names. Defaults to '-'.
     *
     * <p>By default, spans with a timestamp falling on 2016/03/19 end up in the index
     * 'zipkin-span-2016-03-19'. When the date separator is '.', the index would be
     * 'zipkin-span-2016.03.19'. If the date separator is 0, there is no delimiter. Ex the index
     * would be 'zipkin-span-20160319'
     */
    public final Builder dateSeparator(char dateSeparator) {
      indexNameFormatterBuilder().dateSeparator(dateSeparator);
      return this;
    }

    /**
     * The number of shards to split the index into. Each shard and its replicas are assigned to a
     * machine in the cluster. Increasing the number of shards and machines in the cluster will
     * improve read and write performance. Number of shards cannot be changed for existing indices,
     * but new daily indices will pick up changes to the setting. Defaults to 5.
     *
     * <p>Corresponds to <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_shards</a>
     */
    public abstract Builder indexShards(int indexShards);

    /**
     * The number of replica copies of each shard in the index. Each shard and its replicas are
     * assigned to a machine in the cluster. Increasing the number of replicas and machines in the
     * cluster will improve read performance, but not write performance. Number of replicas can be
     * changed for existing indices. Defaults to 1. It is highly discouraged to set this to 0 as it
     * would mean a machine failure results in data loss.
     *
     * <p>Corresponds to <a
     * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html">index.number_of_replicas</a>
     */
    public abstract Builder indexReplicas(int indexReplicas);

    /** {@inheritDoc} */
    @Override public abstract Builder strictTraceId(boolean strictTraceId);

    /** {@inheritDoc} */
    @Override public abstract Builder searchEnabled(boolean searchEnabled);

    /** {@inheritDoc} */
    @Override public abstract Builder autocompleteKeys(List<String> autocompleteKeys);

    /** {@inheritDoc} */
    @Override public abstract Builder autocompleteTtl(int autocompleteTtl);

    /** {@inheritDoc} */
    @Override public abstract Builder autocompleteCardinality(int autocompleteCardinality);

    @Override public abstract ElasticsearchStorage build();

    abstract Builder lazyHttpClient(LazyHttpClient lazyHttpClient);

    abstract IndexNameFormatter.Builder indexNameFormatterBuilder();

    Builder() {
    }
  }

  abstract LazyHttpClient lazyHttpClient();

  @Nullable public abstract String pipeline();

  public abstract boolean flushOnWrites();

  public abstract boolean strictTraceId();

  abstract boolean searchEnabled();

  abstract List<String> autocompleteKeys();

  abstract int autocompleteTtl();

  abstract int autocompleteCardinality();

  abstract int indexShards();

  abstract int indexReplicas();

  public abstract IndexNameFormatter indexNameFormatter();

  public abstract int namesLookback();

  @Override public SpanStore spanStore() {
    ensureIndexTemplates();
    return new ElasticsearchSpanStore(this);
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return (ServiceAndSpanNames) spanStore();
  }

  @Override public AutocompleteTags autocompleteTags() {
    ensureIndexTemplates();
    return new ElasticsearchAutocompleteTags(this);
  }

  @Override public SpanConsumer spanConsumer() {
    ensureIndexTemplates();
    return new ElasticsearchSpanConsumer(this);
  }

  /** Returns the Elasticsearch version of the connected cluster. Internal use only */
  public float version() {
    return ensureIndexTemplates().version();
  }

  char indexTypeDelimiter() {
    return ensureIndexTemplates().indexTypeDelimiter();
  }

  /** This is an internal blocking call, only used in tests. */
  public void clear() throws IOException {
    Set<String> toClear = new LinkedHashSet<>();
    toClear.add(indexNameFormatter().formatType(SPAN));
    toClear.add(indexNameFormatter().formatType(DEPENDENCY));
    for (String index : toClear) clear(index);
  }

  void clear(String index) throws IOException {
    String url = '/' + index;
    AggregatedHttpRequest delete = AggregatedHttpRequest.of(HttpMethod.DELETE, url);
    http().newCall(delete, BodyConverters.NULL, "delete-index").execute();
  }

  /**
   * Internal code and api responses coerce to {@link RejectedExecutionException} when work is
   * rejected. We also classify {@link ResponseTimeoutException} as a capacity related exception
   * eventhough capacity is not the only reason (timeout could also result from a misconfiguration
   * or a network problem).
   */
  @Override public boolean isOverCapacity(Throwable e) {
    return e instanceof RejectedExecutionException || e instanceof ResponseTimeoutException;
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override public CheckResult check() {
    return ensureClusterReady(indexNameFormatter().formatType(SPAN));
  }

  CheckResult ensureClusterReady(String index) {
    try {
      HttpCall.Factory http = http();
      AggregatedHttpRequest request = AggregatedHttpRequest.of(
        HttpMethod.GET, "/_cluster/health/" + index);
      return http.newCall(request, READ_STATUS, "get-cluster-health").execute();
    } catch (IOException | RuntimeException e) {
      // Unwrap the marker exception as the health check is not relevant for the throttle component,
      // and wrapping interferes with humans intended to read this message.
      return CheckResult.failed(e instanceof RejectedExecutionException ? e.getCause() : e);
    }
  }

  @Memoized // since we don't want overlapping calls to apply the index templates
  IndexTemplates ensureIndexTemplates() {
    try {
      IndexTemplates templates = new VersionSpecificTemplates(this).get();
      HttpCall.Factory http = http();
      ensureIndexTemplate(http, buildUrl(templates, SPAN), templates.span());
      ensureIndexTemplate(http, buildUrl(templates, DEPENDENCY), templates.dependency());
      ensureIndexTemplate(http, buildUrl(templates, AUTOCOMPLETE), templates.autocomplete());
      return templates;
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  String buildUrl(IndexTemplates templates, String type) {
    String indexPrefix = indexNameFormatter().index() + templates.indexTypeDelimiter();
    return "/_template/" + indexPrefix + type + "_template";
  }

  @Override public final String toString() {
    return "ElasticsearchStorage{initialEndpoints=" + lazyHttpClient()
      + ", index=" + indexNameFormatter().index() + "}";
  }

  static {
    Internal.instance = new Internal() {
      @Override public HttpCall.Factory http(ElasticsearchStorage storage) {
        return storage.http();
      }
    };
  }

  @Memoized HttpCall.Factory http() {
    return new HttpCall.Factory(lazyHttpClient().get());
  }

  @Override public void close() {
    lazyHttpClient().close();
  }

  ElasticsearchStorage() {
  }

  static final BodyConverter<CheckResult> READ_STATUS = new BodyConverter<CheckResult>() {
    @Override public CheckResult convert(JsonParser parser, Supplier<String> contentString)
      throws IOException {
      JsonParser status = enterPath(parser, "status");
      if (status == null) {
        throw new IllegalArgumentException("Health status couldn't be read " + contentString.get());
      }
      if ("RED".equalsIgnoreCase(status.getText())) {
        return CheckResult.failed(new IllegalStateException("Health status is RED"));
      }
      return CheckResult.OK;
    }

    @Override public String toString() {
      return "ReadStatus";
    }
  };
}
