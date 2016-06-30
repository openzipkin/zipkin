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
package zipkin;

import java.util.Arrays;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.APP_ENDPOINT;

public class SpanTest {

  @Test
  public void idString_withParent() {
    Span withParent = Span.builder().name("foo").traceId(1).id(3).parentId(2L).build();

    assertThat(withParent.idString())
        .isEqualTo("0000000000000001.0000000000000003<:0000000000000002");
  }

  @Test
  public void idString_noParent() {
    Span noParent = Span.builder().name("foo").traceId(1).id(1).build();

    assertThat(noParent.idString())
        .isEqualTo("0000000000000001.0000000000000001<:0000000000000001");
  }

  @Test
  public void spanNamesLowercase() {
    assertThat(Span.builder().traceId(1L).id(1L).name("GET").build().name)
        .isEqualTo("get");
  }

  @Test
  public void mergeWhenBinaryAnnotationsSentSeparately() {
    Span part1 = Span.builder()
        .traceId(1L)
        .name("")
        .id(1L)
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, APP_ENDPOINT))
        .build();

    Span part2 = Span.builder()
        .traceId(1L)
        .name("get")
        .id(1L)
        .timestamp(1444438900939000L)
        .duration(376000L)
        .addAnnotation(Annotation.create(1444438900939000L, Constants.SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create(1444438901315000L, Constants.SERVER_SEND, APP_ENDPOINT))
        .build();

    Span expected = part2.toBuilder()
        .addBinaryAnnotation(part1.binaryAnnotations.get(0))
        .build();

    assertThat(part1.toBuilder().merge(part2).build()).isEqualTo(expected);
    assertThat(part2.toBuilder().merge(part1).build()).isEqualTo(expected);
  }

  /**
   * Some instrumentation set name to "unknown" or empty. This ensures dummy span names lose on
   * merge.
   */
  @Test
  public void mergeOverridesDummySpanNames() {
    for (String nonName : Arrays.asList("", "unknown")) {
      Span unknown = Span.builder().traceId(1).id(2).name(nonName).build();
      Span get = unknown.toBuilder().name("get").build();

      assertThat(unknown.toBuilder().merge(get).build().name).isEqualTo("get");
      assertThat(get.toBuilder().merge(unknown).build().name).isEqualTo("get");
    }
  }

  @Test
  public void serviceNames_includeBinaryAnnotations() {
    Span span = Span.builder()
        .traceId(1L)
        .name("GET")
        .id(1L)
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, APP_ENDPOINT))
        .build();

    assertThat(span.serviceNames())
        .containsOnly(APP_ENDPOINT.serviceName);
  }

  @Test
  public void serviceNames_ignoresAnnotationsWithEmptyServiceNames() {
    Span span = Span.builder()
        .traceId(12345)
        .id(666)
        .name("methodcall")
        .addAnnotation(Annotation.create(1L, "test", Endpoint.create("", 127 << 24 | 1)))
        .addAnnotation(Annotation.create(2L, Constants.SERVER_RECV, APP_ENDPOINT))
        .build();

    assertThat(span.serviceNames())
        .containsOnly(APP_ENDPOINT.serviceName);
  }

  /** This helps tests not flake out when binary annotations aren't returned in insertion order */
  @Test
  public void sortsBinaryAnnotationsByKey() {
    BinaryAnnotation foo = BinaryAnnotation.create("foo", "bar", APP_ENDPOINT);
    BinaryAnnotation baz = BinaryAnnotation.create("baz", "qux", APP_ENDPOINT);
    Span span = Span.builder()
        .traceId(12345)
        .id(666)
        .name("methodcall")
        .addBinaryAnnotation(foo)
        .addBinaryAnnotation(baz)
        .build();

    assertThat(span.binaryAnnotations)
        .containsExactly(baz, foo);
  }

  /** Catches common error when zero is passed instead of null for a timestamp */
  @Test
  public void coercesTimestampZeroToNull() {
    Span span = Span.builder()
        .traceId(1L)
        .name("GET")
        .id(1L)
        .timestamp(0L)
        .build();

    assertThat(span.timestamp)
        .isNull();
  }
}
