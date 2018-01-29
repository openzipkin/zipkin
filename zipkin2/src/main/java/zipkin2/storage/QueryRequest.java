/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage;

import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import zipkin2.Annotation;
import zipkin2.Span;
import zipkin2.internal.Nullable;

/**
 * Invoking this request retrieves traces matching the below filters.
 *
 * <p> Results should be filtered against {@link #endTs}, subject to {@link #limit} and {@link
 * #lookback}. For example, if endTs is 10:20 today, limit is 10, and lookback is 7 days, traces
 * returned should be those nearest to 10:20 today, not 10:20 a week ago.
 *
 * <p> Time units of {@link #endTs} and {@link #lookback} are milliseconds as opposed to
 * microseconds, the grain of {@link Span#timestamp()}. Milliseconds is a more familiar and
 * supported granularity for query, index and windowing functions.
 */
@AutoValue
public abstract class QueryRequest {
  /**
   * When present, corresponds to {@link zipkin2.Endpoint#serviceName} and constrains all other
   * parameters.
   */
  @Nullable public abstract String serviceName();

  /** When present, only include traces with this {@link Span#name} */
  @Nullable public abstract String spanName();

  /**
   * When an input value is the empty string, include traces whose {@link Span#annotations()}
   * include a value in this set, or where {@link Span#tags()} include a key is in this set. When
   * not, include traces whose {@link Span#tags()} an entry in this map.
   *
   * <p>Multiple entries are combined with AND, and AND against other conditions.
   */
  public abstract Map<String, String> annotationQuery();

  /**
   * Only return traces whose {@link Span#duration()} is greater than or equal to minDuration
   * microseconds.
   */
  @Nullable public abstract Long minDuration();

  /**
   * Only return traces whose {@link Span#duration()} is less than or equal to maxDuration
   * microseconds. Only valid with {@link #minDuration}.
   */
  @Nullable public abstract Long maxDuration();

  /**
   * Only return traces where all {@link Span#timestamp()} are at or before this time in epoch
   * milliseconds. Defaults to current time.
   */
  public abstract long endTs();

  /**
   * Only return traces where all {@link Span#timestamp()} are at or after (endTs - lookback) in
   * milliseconds. Defaults to endTs.
   */
  public abstract long lookback();

  /** Maximum number of traces to return. Defaults to 10 */
  public abstract int limit();

  /**
   * Corresponds to query parameter "annotationQuery". Ex. "http.method=GET and error"
   *
   * @see QueryRequest.Builder#parseAnnotationQuery(String)
   */
  @Nullable public String annotationQueryString() {
    StringBuilder result = new StringBuilder();

    for (Iterator<Map.Entry<String, String>> i = annotationQuery().entrySet().iterator();
      i.hasNext(); ) {
      Map.Entry<String, String> next = i.next();
      result.append(next.getKey());
      if (!next.getValue().isEmpty()) result.append('=').append(next.getValue());
      if (i.hasNext()) result.append(" and ");
    }

    return result.length() > 0 ? result.toString() : null;
  }

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new AutoValue_QueryRequest.Builder().annotationQuery(Collections.emptyMap());
  }

  @AutoValue.Builder
  public abstract static class Builder {

    /** @see QueryRequest#serviceName() */
    public abstract Builder serviceName(@Nullable String serviceName);

    /**
     * This ignores the reserved span name "all".
     *
     * @see QueryRequest#spanName()
     */
    public abstract Builder spanName(@Nullable String spanName);

    /**
     * Corresponds to query parameter "annotationQuery". Ex. "http.method=GET and error"
     *
     * @see QueryRequest#annotationQueryString()
     */
    public Builder parseAnnotationQuery(@Nullable String annotationQuery) {
      if (annotationQuery == null || annotationQuery.isEmpty()) return this;
      Map<String, String> map = new LinkedHashMap<>();
      for (String ann : annotationQuery.split(" and ")) {
        int idx = ann.indexOf('=');
        if (idx == -1) {
          map.put(ann, "");
        } else {
          String[] keyValue = ann.split("=");
          map.put(ann.substring(0, idx), keyValue.length < 2 ? "" : ann.substring(idx + 1));
        }
      }
      return annotationQuery(map);
    }

    /** @see QueryRequest#annotationQuery() */
    public abstract Builder annotationQuery(Map<String, String> annotationQuery);

    /** @see QueryRequest#minDuration() */
    public abstract Builder minDuration(@Nullable Long minDuration);

    /** @see QueryRequest#maxDuration() */
    public abstract Builder maxDuration(@Nullable Long maxDuration);

    /** @see QueryRequest#endTs() */
    public abstract Builder endTs(long endTs);

    /** @see QueryRequest#lookback() */
    public abstract Builder lookback(long lookback);

    /** @see QueryRequest#limit() */
    public abstract Builder limit(int limit);

    // getters for validation
    @Nullable abstract String serviceName();

    @Nullable abstract String spanName();

    abstract Map<String, String> annotationQuery();

    @Nullable abstract Long minDuration();

    @Nullable abstract Long maxDuration();

    abstract long endTs();

    abstract int limit();

    abstract QueryRequest autoBuild();

    public final QueryRequest build() {
      // coerce service and span names to lowercase
      if (serviceName() != null) serviceName(serviceName().toLowerCase(Locale.ROOT));
      if (spanName() != null) spanName(spanName().toLowerCase(Locale.ROOT));

      // remove any accidental empty strings
      annotationQuery().remove("");
      if ("".equals(serviceName())) serviceName(null);
      if ("".equals(spanName()) || "all".equals(spanName())) spanName(null);

      if (endTs() <= 0) throw new IllegalArgumentException("endTs <= 0");
      if (limit() <= 0) throw new IllegalArgumentException("limit <= 0");
      if (minDuration() != null) {
        if (minDuration() <= 0) throw new IllegalArgumentException("minDuration <= 0");
        if (maxDuration() != null && maxDuration() < minDuration()) {
          throw new IllegalArgumentException("maxDuration < minDuration");
        }
      } else if (maxDuration() != null) {
        throw new IllegalArgumentException("maxDuration is only valid with minDuration");
      }
      return autoBuild();
    }

    Builder() {
    }
  }

  /**
   * Tests the supplied trace against the current request.
   *
   * <p>This is used when the backend cannot fully refine a trace query.
   */
  public boolean test(List<Span> spans) {
    // v2 returns raw spans in any order, get the root's timestamp or the first timestamp
    long timestamp = 0L;
    for (Span span : spans) {
      if (span.timestampAsLong() == 0L) continue;
      if (span.parentId() == null) {
        timestamp = span.timestampAsLong();
        break;
      }
      if (timestamp == 0L || timestamp > span.timestampAsLong()) {
        timestamp = span.timestampAsLong();
      }
    }
    if (timestamp == 0L ||
      timestamp < (endTs() - lookback()) * 1000 ||
      timestamp > endTs() * 1000) {
      return false;
    }
    Set<String> serviceNames = new LinkedHashSet<>();
    boolean testedDuration = minDuration() == null && maxDuration() == null;

    String spanNameToMatch = spanName();
    Map<String, String> annotationQueryRemaining = new LinkedHashMap<>(annotationQuery());

    for (Span span : spans) {
      String localServiceName = span.localServiceName();

      if (localServiceName != null) serviceNames.add(localServiceName);

      if (serviceName() == null || serviceName().equals(localServiceName)) {
        for (Annotation a : span.annotations()) {
          if ("".equals(annotationQueryRemaining.get(a.value()))) {
            annotationQueryRemaining.remove(a.value());
          }
        }
        for (Map.Entry<String, String> t : span.tags().entrySet()) {
          String value = annotationQueryRemaining.get(t.getKey());
          if (value == null) continue;
          if (value.isEmpty() || value.equals(t.getValue())) {
            annotationQueryRemaining.remove(t.getKey());
          }
        }
        if (spanNameToMatch == null || spanNameToMatch.equals(span.name())) {
          spanNameToMatch = null;
        }
      }

      if ((serviceName() == null || serviceName().equals(localServiceName)) && !testedDuration) {
        if (minDuration() != null && maxDuration() != null) {
          testedDuration = span.durationAsLong() >= minDuration() && span.durationAsLong() <= maxDuration();
        } else if (minDuration() != null) {
          testedDuration = span.durationAsLong() >= minDuration();
        }
      }
    }
    return (serviceName() == null || serviceNames.contains(serviceName()))
      && spanNameToMatch == null
      && annotationQueryRemaining.isEmpty()
      && testedDuration;
  }

  QueryRequest() {
  }
}
