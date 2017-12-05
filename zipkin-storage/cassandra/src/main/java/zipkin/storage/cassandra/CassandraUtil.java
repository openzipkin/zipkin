/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.google.common.base.Function;
import com.google.common.collect.Sets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkArgument;
import static zipkin.internal.Util.sortedList;

final class CassandraUtil {

  /**
   * Zipkin's {@link QueryRequest#binaryAnnotations} are equals match. Not all binary annotations
   * are lookup keys. For example, sql query isn't something that is likely to be looked up by value
   * and indexing that could add a potentially kilobyte partition key on {@link
   * Tables#ANNOTATIONS_INDEX}
   */
  static final int LONGEST_VALUE_TO_INDEX = 256;

  private static final ThreadLocal<CharsetEncoder> UTF8_ENCODER =
      new ThreadLocal<CharsetEncoder>() {
        @Override protected CharsetEncoder initialValue() {
          return UTF_8.newEncoder();
        }
      };

  static ByteBuffer toByteBuffer(String string) throws CharacterCodingException {
    return UTF8_ENCODER.get().encode(CharBuffer.wrap(string));
  }

  /**
   * Returns keys that concatenate the serviceName associated with an annotation or a binary
   * annotation.
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

  static Function<Map<Long, Long>, Set<Long>> keyset() {
    return (Function) KeySet.INSTANCE;
  }

  static BoundStatement bindWithName(PreparedStatement prepared, String name) {
    return new NamedBoundStatement(prepared, name);
  }

  enum KeySet implements Function<Map<Object, ?>, Set<Object>> {
    INSTANCE;

    @Override public Set<Object> apply(@Nullable Map<Object, ?> input) {
      return input.keySet();
    }

    @Override public String toString(){
      return "Map::keySet";
    }
  }

  static Function<List<Map<Long, Long>>, Set<Long>> intersectKeySets() {
    return (Function) IntersectKeySets.INSTANCE;
  }

  enum IntersectKeySets implements Function<List<Map<Object, ?>>, Set<Object>> {
    INSTANCE;

    @Override public Set<Object> apply(@Nullable List<Map<Object, ?>> input) {
      Set<Object> traceIds = Sets.newLinkedHashSet(input.get(0).keySet());
      for (int i = 1; i < input.size(); i++) {
        traceIds.retainAll(input.get(i).keySet());
      }
      return traceIds;
    }

    @Override public String toString(){
      return "IntersectKeySets";
    }
  }
}
