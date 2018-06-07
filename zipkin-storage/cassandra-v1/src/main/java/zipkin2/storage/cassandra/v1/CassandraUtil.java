/*
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
package zipkin2.storage.cassandra.v1;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;

import static com.google.common.base.Preconditions.checkArgument;

final class CassandraUtil {
  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final List<String> CORE_ANNOTATIONS =
      ImmutableList.of("cs", "cr", "ss", "sr", "ms", "mr", "ws", "wr");

  /**
   * Zipkin's {@link QueryRequest#annotationQuery()} are equals match. Not all tags are lookup keys.
   * For example, sql query isn't something that is likely to be looked up by value and indexing
   * that could add a potentially kilobyte partition key on {@link Tables#ANNOTATIONS_INDEX}
   */
  static final int LONGEST_VALUE_TO_INDEX = 256;

  private static final ThreadLocal<CharsetEncoder> UTF8_ENCODER =
      new ThreadLocal<CharsetEncoder>() {
        @Override
        protected CharsetEncoder initialValue() {
          return UTF_8.newEncoder();
        }
      };

  static ByteBuffer toByteBuffer(String string) {
    try {
      return UTF8_ENCODER.get().encode(CharBuffer.wrap(string));
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Returns keys that concatenate the serviceName associated with an annotation or a binary
   * annotation.
   *
   * <p>Note: in the case of binary annotations, only string types are returned, as that's the only
   * queryable type, per {@link QueryRequest#annotationQuery()}.
   *
   * @see QueryRequest#annotationQuery()
   */
  static Set<String> annotationKeys(V1Span span) {
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (V1Annotation a : span.annotations()) {
      // don't index core annotations as they aren't queryable
      if (CORE_ANNOTATIONS.contains(a.value())) continue;

      if (a.endpoint() != null && a.endpoint().serviceName() != null) {
        annotationKeys.add(a.endpoint().serviceName() + ":" + a.value());
      }
    }
    for (V1BinaryAnnotation b : span.binaryAnnotations()) {
      if (b.stringValue() != null
          && b.endpoint() != null
          && b.endpoint().serviceName() != null
          && b.stringValue().length() <= LONGEST_VALUE_TO_INDEX) {
        String value = b.stringValue();
        if (value.length() > LONGEST_VALUE_TO_INDEX) continue;

        annotationKeys.add(b.endpoint().serviceName() + ":" + b.key());
        annotationKeys.add(b.endpoint().serviceName() + ":" + b.key() + ":" + value);
      }
    }
    return annotationKeys;
  }

  static List<String> annotationKeys(QueryRequest request) {
    if (request.annotationQuery().isEmpty()) return Collections.emptyList();
    checkArgument(request.serviceName() != null, "serviceName needed with annotation query");
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (Map.Entry<String, String> e : request.annotationQuery().entrySet()) {
      if (e.getValue().isEmpty()) {
        annotationKeys.add(request.serviceName() + ":" + e.getKey());
      } else {
        annotationKeys.add(request.serviceName() + ":" + e.getKey() + ":" + e.getValue());
      }
    }
    return sortedList(annotationKeys);
  }

  static <T extends Comparable<? super T>> List<T> sortedList(@Nullable Collection<T> in) {
    if (in == null || in.isEmpty()) return Collections.emptyList();
    if (in.size() == 1) return Collections.singletonList(in.iterator().next());
    Object[] array = in.toArray();
    Arrays.sort(array);
    List result = Arrays.asList(array);
    return Collections.unmodifiableList(result);
  }

  enum KeySet implements Function<Map<Object, ?>, Set<Object>> {
    INSTANCE;

    @Override
    public Set<Object> apply(@Nullable Map<Object, ?> input) {
      return input.keySet();
    }

    @Override
    public String toString() {
      return "Map::keySet";
    }
  }

  static final Random RAND = new Random(System.nanoTime());
  static final BigInteger OFFSET = BigInteger.valueOf(Integer.MAX_VALUE);

  static Set<Long> sortTraceIdsByDescTimestamp(Set<Pair> set) {
    // timestamps can collide, so we need to add some random digits on end before using them as
    // serviceSpanKeys
    SortedMap<BigInteger, Long> sorted = new TreeMap<>(Collections.reverseOrder());
    for (Pair pair : set) {
      BigInteger uncollided =
          BigInteger.valueOf(pair.right)
              .multiply(OFFSET)
              .add(BigInteger.valueOf(RAND.nextInt() & Integer.MAX_VALUE));
      sorted.put(uncollided, pair.left);
    }
    return new LinkedHashSet<>(sorted.values());
  }

  static Call.Mapper<Set<Pair>, Set<Long>> sortTraceIdsByDescTimestampMapper() {
    return SortTraceIdsByDescTimestamp.INSTANCE;
  }

  enum SortTraceIdsByDescTimestamp implements Call.Mapper<Set<Pair>, Set<Long>> {
    INSTANCE;

    @Override
    public Set<Long> map(Set<Pair> set) {
      return sortTraceIdsByDescTimestamp(set);
    }

    @Override
    public String toString() {
      return "SortTraceIdsByDescTimestamp";
    }
  }
}
