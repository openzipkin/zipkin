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
package zipkin.internal;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.CodecTest;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public final class JsonCodecTest extends CodecTest {

  @Override
  protected JsonCodec codec() {
    return Codec.JSON;
  }

  @Test
  public void tracesRoundTrip() throws IOException {
    List<List<Span>> traces = asList(TestObjects.TRACE, TestObjects.TRACE);
    byte[] bytes = codec().writeTraces(traces);
    assertThat(codec().readTraces(bytes))
        .isEqualTo(traces);
  }

  @Test
  public void stringsRoundTrip() throws IOException {
    List<String> strings = asList("foo", "bar", "baz");
    byte[] bytes = codec().writeStrings(strings);
    assertThat(codec().readStrings(bytes))
        .isEqualTo(strings);
  }

  @Test
  public void ignoreNull_parentId() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"parentId\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void ignoreNull_timestamp() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"timestamp\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void ignoreNull_duration() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"duration\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void ignoreNull_debug() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"debug\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void ignoreNull_annotation_endpoint() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"annotations\": [\n"
        + "    {\n"
        + "      \"timestamp\": 1461750491274000,\n"
        + "      \"value\": \"cs\",\n"
        + "      \"endpoint\": null\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void ignoreNull_binaryAnnotation_endpoint() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"lc\",\n"
        + "      \"value\": \"JDBCSpanStore\",\n"
        + "      \"endpoint\": null\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void niceErrorOnNull_traceId() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
        + "  \"traceId\": null,\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void niceErrorOnNull_id() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Expected a string but was NULL");

    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": null\n"
        + "}";

    Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
  }

  @Test
  public void binaryAnnotation_long() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"num\",\n"
        + "      \"value\": 123456789,\n"
        + "      \"type\": \"I64\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
    assertThat(span.binaryAnnotations)
        .containsExactly(BinaryAnnotation.builder()
            .key("num")
            .type(BinaryAnnotation.Type.I64)
            .value(toBytes(123456789))
            .build());

    assertThat(Codec.JSON.readSpan(Codec.JSON.writeSpan(span)))
        .isEqualTo(span);
  }

  @Test
  public void binaryAnnotation_double() {
    String json = "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"num\",\n"
        + "      \"value\": 1.23456789,\n"
        + "      \"type\": \"DOUBLE\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span span = Codec.JSON.readSpan(json.getBytes(Util.UTF_8));
    assertThat(span.binaryAnnotations)
        .containsExactly(BinaryAnnotation.builder()
            .key("num")
            .type(BinaryAnnotation.Type.DOUBLE)
            .value(toBytes(Double.doubleToRawLongBits(1.23456789)))
            .build());

    assertThat(Codec.JSON.readSpan(Codec.JSON.writeSpan(span)))
        .isEqualTo(span);
  }

  @Test
  public void sizeInBytes_span() throws IOException {
    Span span = TestObjects.LOTS_OF_SPANS[0];
    assertThat(JsonCodec.SPAN_ADAPTER.sizeInBytes(span))
        .isEqualTo(codec().writeSpan(span).length);
  }

  @Test
  public void sizeInBytes_link() throws IOException {
    assertThat(JsonCodec.DEPENDENCY_LINK_ADAPTER.sizeInBytes(TestObjects.LINKS.get(0)))
        .isEqualTo(codec().writeDependencyLink(TestObjects.LINKS.get(0)).length);
  }

  static byte[] toBytes(long v) {
    okio.Buffer buffer = new okio.Buffer();
    buffer.writeLong(v);
    return buffer.readByteArray();
  }
}
