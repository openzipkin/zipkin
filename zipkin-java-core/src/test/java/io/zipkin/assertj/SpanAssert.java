/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.assertj;

import io.zipkin.BinaryAnnotation;
import io.zipkin.Span;
import io.zipkin.internal.Util;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.internal.ByteArrays;
import org.assertj.core.internal.Objects;

public final class SpanAssert extends AbstractAssert<SpanAssert, Span> {

  ByteArrays arrays = ByteArrays.instance();
  Objects objects = Objects.instance();

  public SpanAssert(Span actual) {
    super(actual, SpanAssert.class);
  }

  public SpanAssert hasName(String expected) {
    isNotNull();
    objects.assertEqual(info, actual.name, expected);
    return this;
  }

  public SpanAssert hasTimestamp(long expected) {
    isNotNull();
    objects.assertEqual(info, actual.timestamp, expected);
    return this;
  }

  public SpanAssert hasDuration(long expected) {
    isNotNull();
    objects.assertEqual(info, actual.duration, expected);
    return this;
  }

  public SpanAssert hasBinaryAnnotation(String key, String utf8Expected) {
    isNotNull();
    return hasBinaryAnnotation(key, utf8Expected.getBytes(Util.UTF_8));
  }

  public SpanAssert hasBinaryAnnotation(final String key, byte[] expected) {
    isNotNull();
    List<String> keys = new ArrayList<>();
    for (BinaryAnnotation b : actual.binaryAnnotations) {
      if (b.key.equals(key)) {
        arrays.assertContains(info, b.value, expected);
        return this;
      }
      keys.add(b.key);
    }
    failWithMessage("\nExpecting binaryAnnotation keys to contain %s, was: <%s>", key, keys);
    return this;
  }
}
