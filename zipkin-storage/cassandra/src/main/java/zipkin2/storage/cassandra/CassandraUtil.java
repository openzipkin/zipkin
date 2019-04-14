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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.LocalDate;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.DateUtil;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;

final class CassandraUtil {
  /**
   * Zipkin's {@link QueryRequest#annotationQuery()} are equals match. Not all tag serviceSpanKeys
   * are lookup serviceSpanKeys. For example, {@code sql.query} isn't something that is likely to be
   * looked up by value and indexing that could add a potentially kilobyte partition key on {@link
   * Schema#TABLE_SPAN}
   */
  static final int LONGEST_VALUE_TO_INDEX = 256;

  /**
   * Time window covered by a single bucket of the {@link Schema#TABLE_TRACE_BY_SERVICE_SPAN} and
   * {@link Schema#TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE}, in seconds. Default: 1 day
   */
  private static final long DURATION_INDEX_BUCKET_WINDOW_SECONDS =
      Long.getLong("zipkin.store.cassandra.internal.durationIndexBucket", 24 * 60 * 60);

  public static int durationIndexBucket(long ts_micro) {
    // if the window constant has microsecond precision, the division produces negative getValues
    return (int) (ts_micro / (DURATION_INDEX_BUCKET_WINDOW_SECONDS * 1_000_000));
  }

  /**
   * Returns a set of annotation getValues and tags joined on equals, delimited by ░
   *
   * @see QueryRequest#annotationQuery()
   */
  static @Nullable String annotationQuery(Span span) {
    if (span.annotations().isEmpty() && span.tags().isEmpty()) return null;

    char delimiter = '░'; // as very unlikely to be in the query
    StringBuilder result = new StringBuilder().append(delimiter);
    for (Annotation a : span.annotations()) {
      if (a.value().length() > LONGEST_VALUE_TO_INDEX) continue;

      result.append(a.value()).append(delimiter);
    }

    for (Map.Entry<String, String> tag : span.tags().entrySet()) {
      if (tag.getValue().length() > LONGEST_VALUE_TO_INDEX) continue;

      result.append(tag.getKey()).append(delimiter); // search is possible by key alone
      result.append(tag.getKey() + "=" + tag.getValue()).append(delimiter);
    }
    return result.length() == 1 ? null : result.toString();
  }

  static List<String> annotationKeys(QueryRequest request) {
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (Map.Entry<String, String> e : request.annotationQuery().entrySet()) {
      if (e.getValue().isEmpty()) {
        annotationKeys.add(e.getKey());
      } else {
        annotationKeys.add(e.getKey() + "=" + e.getValue());
      }
    }
    return new ArrayList<>(annotationKeys);
  }

  static Call.Mapper<Map<String, Long>, Set<String>> traceIdsSortedByDescTimestamp() {
    return TraceIdsSortedByDescTimestamp.INSTANCE;
  }

  enum TraceIdsSortedByDescTimestamp implements Call.Mapper<Map<String, Long>, Set<String>> {
    INSTANCE;

    @Override
    public Set<String> map(Map<String, Long> map) {
      // timestamps can collide, so we need to add some random digits on end before using them as
      // serviceSpanKeys
      SortedMap<BigInteger, String> sorted = new TreeMap<>(Collections.reverseOrder());
      for (Map.Entry<String, Long> entry : map.entrySet()) {
        BigInteger uncollided =
            BigInteger.valueOf(entry.getValue())
                .multiply(OFFSET)
                .add(BigInteger.valueOf(RAND.nextInt() & Integer.MAX_VALUE));
        sorted.put(uncollided, entry.getKey());
      }
      return new LinkedHashSet<>(sorted.values());
    }

    @Override
    public String toString() {
      return "TraceIdsSortedByDescTimestamp";
    }

    private static final Random RAND = new Random(System.nanoTime());
    private static final BigInteger OFFSET = BigInteger.valueOf(Integer.MAX_VALUE);
  }

  static List<LocalDate> getDays(long endTs, @Nullable Long lookback) {
    List<LocalDate> result = new ArrayList<>();
    for (Date javaDate : DateUtil.getDays(endTs, lookback)) {
      result.add(LocalDate.fromMillisSinceEpoch(javaDate.getTime()));
    }
    return result;
  }
}
