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

package zipkin.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.google.common.base.Function;
import com.google.common.collect.Sets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.QueryRequest;
import zipkin.Span;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.sortedList;

final class CassandraUtil {

  // Time window covered by a single bucket of the Span Duration Index, in seconds. Default: 1hr
  private static final long DURATION_INDEX_BUCKET_WINDOW_SECONDS
      = Long.getLong("zipkin.store.cassandra.internal.durationIndexBucket", 60 * 60);

  public static int durationIndexBucket(long ts) {
    // if the window constant has microsecond precision, the division produces negative values
    return (int) ((ts / DURATION_INDEX_BUCKET_WINDOW_SECONDS) / 1000000);
  }

  private static final ThreadLocal<CharsetEncoder> UTF8_ENCODER =
      new ThreadLocal<CharsetEncoder>() {
        @Override protected CharsetEncoder initialValue() {
          return UTF_8.newEncoder();
        }
      };

  static ByteBuffer toByteBuffer(String string) throws CharacterCodingException {
    return UTF8_ENCODER.get().encode(CharBuffer.wrap(string));
  }

  static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {
    @Override protected SimpleDateFormat initialValue() {
      SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
      result.setTimeZone(TimeZone.getTimeZone("UTC"));
      return result;
    }
  };

  static String iso8601(long timestamp) {
    return DATE_FORMATTER.get().format(new Date(timestamp / 1000));
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
  static List<String> annotationKeys(Span span) {
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (Annotation a : span.annotations) {
      if (a.endpoint != null && !a.endpoint.serviceName.isEmpty()) {
        annotationKeys.add(a.endpoint.serviceName + ":" + a.value);
      }
    }
    for (BinaryAnnotation b : span.binaryAnnotations) {
      if (b.type == BinaryAnnotation.Type.STRING
          && b.endpoint != null
          && !b.endpoint.serviceName.isEmpty()) {
        annotationKeys.add(b.endpoint.serviceName + ":" + b.key);
        annotationKeys.add(b.endpoint.serviceName + ":" + b.key + ":" + new String(b.value, UTF_8));
      }
    }
    return sortedList(annotationKeys);
  }

  static List<String> annotationKeys(QueryRequest request) {
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (String a : request.annotations) {
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

    @Override public Set<Object> apply(Map<Object, ?> input) {
      return input.keySet();
    }
  }

  static Function<List<Map<Long, Long>>, Set<Long>> intersectKeySets() {
    return (Function) IntersectKeySets.INSTANCE;
  }

  enum IntersectKeySets implements Function<List<Map<Object, ?>>, Set<Object>> {
    INSTANCE;

    @Override public Set<Object> apply(List<Map<Object, ?>> input) {
      Set<Object> traceIds = Sets.newLinkedHashSet(input.get(0).keySet());
      for (int i = 1; i < input.size(); i++) {
        traceIds.retainAll(input.get(i).keySet());
      }
      return traceIds;
    }
  }
}
