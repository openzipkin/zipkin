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
package zipkin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class CodecTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  protected abstract Codec codec();

  @Test
  public void spanRoundTrip() throws IOException {
    for (Span span : TestObjects.TRACE) {
      byte[] bytes = codec().writeSpan(span);
      assertThat(codec().readSpan(bytes))
          .isEqualTo(span);
    }
  }

  @Test
  public void sizeInBytes() throws IOException {
    for (Span span : TestObjects.TRACE) {
      assertThat(codec().sizeInBytes(span))
          .isEqualTo(codec().writeSpan(span).length);
    }
  }

  @Test
  public void spanRoundTrip_128bitTraceId() throws IOException {
    for (Span span : TestObjects.TRACE) {
      span = span.toBuilder().traceIdHigh(12345L).build();
      byte[] bytes = codec().writeSpan(span);
      assertThat(codec().readSpan(bytes))
          .isEqualTo(span);
    }
  }

  @Test
  public void sizeInBytes_128bitTraceId() throws IOException {
    for (Span span : TestObjects.TRACE) {
      span = span.toBuilder().traceIdHigh(12345L).build();
      assertThat(codec().sizeInBytes(span))
          .isEqualTo(codec().writeSpan(span).length);
    }
  }

  @Test
  public void binaryAnnotation_long() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0].toBuilder().binaryAnnotations(asList(
        BinaryAnnotation.builder()
            .key("Long.zero")
            .type(BinaryAnnotation.Type.I64)
            .value(ByteBuffer.allocate(8).putLong(0, 0L).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Long.negative")
            .type(BinaryAnnotation.Type.I64)
            .value(ByteBuffer.allocate(8).putLong(0, -1005656679588439279L).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Long.MIN_VALUE")
            .type(BinaryAnnotation.Type.I64)
            .value(ByteBuffer.allocate(8).putLong(0, Long.MIN_VALUE).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Long.MAX_VALUE")
            .type(BinaryAnnotation.Type.I64)
            .value(ByteBuffer.allocate(8).putLong(0, Long.MAX_VALUE).array())
            .build()
    )).build();

    byte[] bytes = codec().writeSpan(span);
    assertThat(codec().readSpan(bytes))
        .isEqualTo(span);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that
   * trip-up json don't fail in codec.
   */
  @Test
  public void specialCharsInJson() throws IOException {
    // service name is surrounded by control characters
    Endpoint e = Endpoint.create(new String(new char[] {0, 'a', 1}), 0);
    Span worstSpanInTheWorld = Span.builder().traceId(1L).id(1L)
        // name is terrible
        .name(new String(new char[] {'"', '\\', '\t', '\b', '\n', '\r', '\f'}))
        // annotation value includes some json newline characters
        .addAnnotation(Annotation.create(1L, "\u2028 and \u2029", e))
        // binary annotation key includes a quote and value newlines
        .addBinaryAnnotation(BinaryAnnotation.create("\"foo",
            "Database error: ORA-00942:\u2028 and \u2029 table or view does not exist\n", e))
        .build();

    byte[] bytes = codec().writeSpan(worstSpanInTheWorld);
    assertThat(codec().readSpan(bytes))
        .isEqualTo(worstSpanInTheWorld);
  }

  @Test
  public void binaryAnnotation_double() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0].toBuilder().binaryAnnotations(asList(
        BinaryAnnotation.builder()
            .key("Double.zero")
            .type(BinaryAnnotation.Type.DOUBLE)
            .value(ByteBuffer.allocate(8).putDouble(0, 0.0).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Double.negative")
            .type(BinaryAnnotation.Type.DOUBLE)
            .value(ByteBuffer.allocate(8).putDouble(0, -1.005656679588439279).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Double.MIN_VALUE")
            .type(BinaryAnnotation.Type.DOUBLE)
            .value(ByteBuffer.allocate(8).putDouble(0, Double.MIN_VALUE).array())
            .build(),
        BinaryAnnotation.builder()
            .key("Double.MAX_VALUE")
            .type(BinaryAnnotation.Type.I64)
            .value(ByteBuffer.allocate(8).putDouble(0, Double.MAX_VALUE).array())
            .build()
    )).build();

    byte[] bytes = codec().writeSpan(span);
    assertThat(codec().readSpan(bytes))
        .isEqualTo(span);
  }

  @Test
  public void spansRoundTrip() throws IOException {
    byte[] bytes = codec().writeSpans(TestObjects.TRACE);
    assertThat(codec().readSpans(bytes))
        .isEqualTo(TestObjects.TRACE);
  }

  @Test
  public void writeTraces() throws IOException {
    byte[] bytes = codec().writeTraces(asList(TestObjects.TRACE, TestObjects.TRACE));
    assertThat(codec().writeSpans(TestObjects.TRACE).length * 2)
        .isLessThan(bytes.length);
  }

  @Test
  public void dependencyLinkRoundTrip() throws IOException {
    DependencyLink link = DependencyLink.create("foo", "bar", 2);
    byte[] bytes = codec().writeDependencyLink(link);
    assertThat(codec().readDependencyLink(bytes))
        .isEqualTo(link);
  }

  @Test
  public void dependencyLinkRoundTrip_withError() throws IOException {
    DependencyLink link = DependencyLink.builder()
      .parent("foo")
      .child("bar")
      .callCount(2)
      .errorCount(1).build();

    byte[] bytes = codec().writeDependencyLink(link);
    assertThat(codec().readDependencyLink(bytes))
      .isEqualTo(link);
  }

  @Test
  public void dependencyLinksRoundTrip() throws IOException {
    List<DependencyLink> links = asList(
        DependencyLink.create("foo", "bar", 2),
        DependencyLink.create("bar", "baz", 3)
    );
    byte[] bytes = codec().writeDependencyLinks(links);
    assertThat(codec().readDependencyLinks(bytes))
        .isEqualTo(links);
  }

  @Test
  public void dependencyLinksRoundTrip_withError() throws IOException {
    List<DependencyLink> links = asList(
      DependencyLink.create("foo", "bar", 2),
      DependencyLink.builder()
        .parent("bar")
        .child("baz")
        .callCount(3)
        .errorCount(1).build()
    );
    byte[] bytes = codec().writeDependencyLinks(links);
    assertThat(codec().readDependencyLinks(bytes))
      .isEqualTo(links);
  }

  @Test
  public void decentErrorMessageOnEmptyInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading Span");

    codec().readSpan(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnEmptyInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<Span>");

    codec().readSpans(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnEmptyInput_dependencyLinks() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Empty input reading List<DependencyLink>");

    codec().readDependencyLinks(new byte[0]);
  }

  @Test
  public void decentErrorMessageOnMalformedInput_span() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading Span from ");

    codec().readSpan(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  /**
   * Particulary, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test
  public void decentErrorMessageOnMalformedInput_spans() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<Span> from ");

    codec().readSpans(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }

  @Test
  public void decentErrorMessageOnMalformedInput_dependencyLinks() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Malformed reading List<DependencyLink> from ");

    codec().readDependencyLinks(new byte[] {'h', 'e', 'l', 'l', 'o'});
  }
}
