/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

package zipkin.storage.cassandra3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.google.common.base.Function;
import com.google.common.collect.Sets;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Span;
import zipkin.storage.QueryRequest;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkArgument;
import static zipkin.internal.Util.sortedList;

final class CassandraUtil {

  /**
   * Zipkin's {@link QueryRequest#binaryAnnotations} are equals match. Not all binary annotations
   * are lookup keys. For example, sql query isn't something that is likely to be looked up by value
   * and indexing that could add a potentially kilobyte partition key on {@link Schema#TABLE_TRACES}
   */
  static final int LONGEST_VALUE_TO_INDEX = 256;

  // Time window covered by a single bucket of the Span Duration Index, in seconds. Default: 1 day
  private static final long DURATION_INDEX_BUCKET_WINDOW_SECONDS
      = Long.getLong("zipkin.store.cassandra.internal.durationIndexBucket", 24 * 60 * 60);

  public static int durationIndexBucket(long ts_micro) {
    // if the window constant has microsecond precision, the division produces negative values
    return (int) ((ts_micro / DURATION_INDEX_BUCKET_WINDOW_SECONDS) / 1000000);
  }

  /**
   * Returns keys that concatenate the serviceName associated with an annotation, a binary
   * annotation key, or a binary annotation key with value.
   *
   * <p>Note: in the case of binary annotations, only string types are returned, as that's the only
   * queryable type, per {@link QueryRequest#binaryAnnotations}.
   *
   * @see QueryRequest#annotations
   * @see QueryRequest#binaryAnnotations
   */
  static Set<String> annotationKeys(Span span) {
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (Annotation a : span.annotations) {
      // don't index core annotations as they aren't queryable
      if (Constants.CORE_ANNOTATIONS.contains(a.value)) continue;

      if (a.endpoint != null && !a.endpoint.serviceName.isEmpty()) {
        annotationKeys.add(a.endpoint.serviceName + ":" + a.value);
      }
    }
    for (BinaryAnnotation b : span.binaryAnnotations) {
      if (b.type == BinaryAnnotation.Type.STRING
          && b.endpoint != null
          && !b.endpoint.serviceName.isEmpty()
          && b.value.length <= LONGEST_VALUE_TO_INDEX * 4) { // UTF_8 is up to 4bytes/char
        String value = new String(b.value, UTF_8);
        if (value.length() > LONGEST_VALUE_TO_INDEX) continue;

        annotationKeys.add(b.endpoint.serviceName + ":" + b.key);
        annotationKeys.add(b.endpoint.serviceName + ":" + b.key + ":" + new String(b.value, UTF_8));
      }
    }
    return annotationKeys;
  }

  static List<String> annotationKeys(QueryRequest request) {
    if (request.annotations.isEmpty() && request.binaryAnnotations.isEmpty()) {
      return Collections.emptyList();
    }
    checkArgument(request.serviceName != null, "serviceName needed with annotation query");
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (String a : request.annotations) { // doesn't include CORE_ANNOTATIONS
      annotationKeys.add(request.serviceName + ":" + a);
    }
    for (Map.Entry<String, String> b : request.binaryAnnotations.entrySet()) {
      annotationKeys.add(request.serviceName + ":" + b.getKey() + ":" + b.getValue());
    }
    return sortedList(annotationKeys);
  }

  static Function<Map<BigInteger, Long>, Set<BigInteger>> keyset() {
    return (Function) KeySet.INSTANCE;
  }

  static BoundStatement bindWithName(PreparedStatement prepared, String name) {
    return new NamedBoundStatement(prepared, name);
  }

  /** Used to assign a friendly name when tracing and debugging */
  static final class NamedBoundStatement extends BoundStatement {

    final String name;

    NamedBoundStatement(PreparedStatement statement, String name) {
      super(statement);
      this.name = name;
    }

    @Override public String toString() {
      return name;
    }
  }

  enum KeySet implements Function<Map<Object, ?>, Set<Object>> {
    INSTANCE;

    @Override public Set<Object> apply(Map<Object, ?> input) {
      return input.keySet();
    }
  }

  static Function<List<Map<BigInteger, Long>>, Collection<BigInteger>> intersectKeySets() {
    return (Function) IntersectKeySets.INSTANCE;
  }

  static Function<Map<BigInteger, Long>, Collection<BigInteger>> traceIdsSortedByDescTimestamp() {
    return TraceIdsSortedByDescTimestamp.INSTANCE;
  }

  enum IntersectKeySets implements Function<List<Map<Object, ?>>, Collection<Object>> {
    INSTANCE;

    @Override public Collection<Object> apply(List<Map<Object, ?>> input) {
      Set<Object> traceIds = Sets.newLinkedHashSet(input.get(0).keySet());
      for (int i = 1; i < input.size(); i++) {
        traceIds.retainAll(input.get(i).keySet());
      }
      return traceIds;
    }
  }

  enum TraceIdsSortedByDescTimestamp
      implements Function<Map<BigInteger, Long>, Collection<BigInteger>> {
    INSTANCE;

    @Override public Collection<BigInteger> apply(Map<BigInteger, Long> map) {
      // timestamps can collide, so we need to add some random digits on end before using them as keys
      SortedMap<BigInteger, BigInteger> sorted = new TreeMap<>(Collections.reverseOrder());
      for (Map.Entry<BigInteger, Long> e : map.entrySet()) {
        sorted.put(
            BigInteger.valueOf(e.getValue())
                .multiply(OFFSET)
                .add(BigInteger.valueOf(RAND.nextInt())),
            e.getKey());
      }
      return sorted.values();
    }

    private static final Random RAND = new Random(System.nanoTime());
    private static final BigInteger OFFSET = BigInteger.valueOf(Integer.MAX_VALUE);
  }
}
