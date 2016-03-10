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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.twitter.zipkin.storage.cassandra.Repository;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.internal.Util;

import static zipkin.internal.Util.UTF_8;

final class CassandraUtil {
  static final CharsetEncoder UTF8_ENCODER = Util.UTF_8.newEncoder();

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
  static List<ByteBuffer> annotationKeys(Span span) {
    // Perform distinct with strings, since ByteBuffers don't do content-based hashCodes
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
    return toByteBuffers(annotationKeys);
  }

  static List<ByteBuffer> annotationKeys(QueryRequest request) {
    // Perform distinct with strings, since ByteBuffers don't deal do content-based hashCodes
    Set<String> annotationKeys = new LinkedHashSet<>();
    for (String a : request.annotations) {
      annotationKeys.add(request.serviceName + ":" + a);
    }
    for (Map.Entry<String, String> b : request.binaryAnnotations.entrySet()) {
      annotationKeys.add(request.serviceName + ":" + b.getKey() + ":" + b.getValue());
    }
    return toByteBuffers(annotationKeys);
  }

  /** Eventhough the input is always a string, {@link Repository} requires byte buffer inputs. */
  private static List<ByteBuffer> toByteBuffers(Collection<String> strings) {
    if (strings.isEmpty()) return Collections.emptyList();
    List<ByteBuffer> result = new ArrayList<>(strings.size());
    for (String string : strings) {
      try {
        result.add(UTF8_ENCODER.encode(CharBuffer.wrap(string)));
      } catch (CharacterCodingException ignored) {
        // don't die if the encoding is unknown
      }
    }
    return result;
  }
}
