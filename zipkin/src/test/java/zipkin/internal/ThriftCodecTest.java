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
package zipkin.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.CodecTest;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public final class ThriftCodecTest extends CodecTest {
  private final ThriftCodec codec = new ThriftCodec();

  @Override
  protected ThriftCodec codec() {
    return codec;
  }

  @Test
  public void readSpanFromByteBuffer() throws IOException {
    for (Span span : TestObjects.TRACE) {
      byte[] bytes = codec().writeSpan(span);
      assertThat(codec().readSpan(ByteBuffer.wrap(bytes)))
          .isEqualTo(span);
    }
  }

  @Test
  public void sizeInBytes_span() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0];
    assertThat(ThriftCodec.SPAN_WRITER.sizeInBytes(span))
        .isEqualTo(codec().writeSpan(span).length);
  }

  @Test
  public void sizeInBytes_trace() throws IOException {
    assertThat(ThriftCodec.listSizeInBytes(ThriftCodec.SPAN_WRITER, TestObjects.TRACE))
        .isEqualTo(codec().writeSpans(TestObjects.TRACE).length);
  }

  @Test
  public void sizeInBytes_links() throws IOException {
    assertThat(ThriftCodec.listSizeInBytes(ThriftCodec.DEPENDENCY_LINK_ADAPTER, TestObjects.LINKS))
        .isEqualTo(codec().writeDependencyLinks(TestObjects.LINKS).length);
  }

  @Test
  public void readDependencyLinksFromByteBuffer() throws IOException {
    List<DependencyLink> links = asList(
        DependencyLink.create("foo", "bar", 2),
        DependencyLink.builder()
          .parent("bar")
          .child("baz")
          .callCount(3)
          .errorCount(1).build()
    );
    byte[] bytes = codec().writeDependencyLinks(links);
    assertThat(codec().readDependencyLinks(ByteBuffer.wrap(bytes)))
        .isEqualTo(links);
  }

  @Test
  public void writeLargeAnnotations() {
    Span span = TestObjects.LOTS_OF_SPANS[0].toBuilder().binaryAnnotations(asList(
      BinaryAnnotation.builder()
        .key("Large.value")
        .type(BinaryAnnotation.Type.BYTES)
        .value(new byte[ThriftCodec.STRING_LENGTH_LIMIT + 1])
        .build()
    )).build();

    thrown.expect(AssertionError.class);
    thrown.expectMessage("Could not write");
    codec().writeSpan(span);
  }

  @Test
  public void writeLargeSpanName() {
    StringBuilder sb = new StringBuilder();
    IntStream.range(0, ThriftCodec.STRING_LENGTH_LIMIT + 1).forEach((i) -> sb.append("."));

    Span span = Span.builder()
      .traceId(0L)
      .parentId(0L)
      .id(0L)
      .name(sb.toString())
      .build();

    thrown.expect(AssertionError.class);
    thrown.expectMessage("Could not write");
    codec().writeSpan(span);
  }
}
