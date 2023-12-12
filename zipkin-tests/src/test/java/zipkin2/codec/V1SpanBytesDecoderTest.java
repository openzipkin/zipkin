/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin2.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.codec.SpanBytesEncoderTest.LOCAL_SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.NO_ANNOTATIONS_ROOT_SERVER_SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.UTF8_SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.UTF_8;

/** V1 tests for {@link SpanBytesDecoderTest} */
public class V1SpanBytesDecoderTest {
  Span span = SPAN;

  @Test void niceErrorOnTruncatedSpans_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      byte[] encoded = SpanBytesEncoder.THRIFT.encodeList(TRACE);
      SpanBytesDecoder.THRIFT.decodeList(Arrays.copyOfRange(encoded, 0, 10));
    });
    assertTrue(exception.getMessage()
      .contains("Truncated: length 8 > bytes available 2 reading List<Span> from TBinary"));
  }

  @Test void niceErrorOnTruncatedSpan_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      byte[] encoded = SpanBytesEncoder.THRIFT.encode(SPAN);
      SpanBytesDecoder.THRIFT.decodeOne(Arrays.copyOfRange(encoded, 0, 10));
    });
    assertTrue(exception.getMessage()
      .contains("Truncated: length 8 > bytes available 7 reading Span from TBinary"));
  }

  @Test void emptyListOk_THRIFT() {
    assertThat(SpanBytesDecoder.THRIFT.decodeList(new byte[0]))
      .isEmpty(); // instead of throwing an exception

    byte[] emptyListLiteral = {12 /* TYPE_STRUCT */, 0, 0, 0, 0 /* zero length */};
    assertThat(SpanBytesDecoder.THRIFT.decodeList(emptyListLiteral))
      .isEmpty(); // instead of throwing an exception
  }

  @Test void spanRoundTrip_JSON_V1() {
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
        .isEqualTo(span);
  }

  @Test void spanRoundTrip_THRIFT() {
    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
        .isEqualTo(span);
  }

  @Test void localSpanRoundTrip_JSON_V1() {
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(LOCAL_SPAN)))
        .isEqualTo(LOCAL_SPAN);
  }

  @Test void localSpanRoundTrip_THRIFT() {
    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(LOCAL_SPAN)))
        .isEqualTo(LOCAL_SPAN);
  }

  @Test void spanRoundTrip_64bitTraceId_JSON_V1() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
        .isEqualTo(span);
  }

  @Test void spanRoundTrip_64bitTraceId_THRIFT() {
    span = span.toBuilder().traceId(span.traceId().substring(16)).build();

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
        .isEqualTo(span);
  }

  @Test void spanRoundTrip_shared_JSON_V1() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
        .isEqualTo(span);
  }

  @Test void spanRoundTrip_shared_THRIFT() {
    span = span.toBuilder().kind(Span.Kind.SERVER).shared(true).build();

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
        .isEqualTo(span);
  }

  /**
   * This isn't a test of what we "should" accept as a span, rather that characters that trip-up
   * json don't fail in codec.
   */
  @Test void specialCharsInJson_JSON_V1() {
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(UTF8_SPAN)))
        .isEqualTo(UTF8_SPAN);
  }

  @Test void specialCharsInJson_THRIFT() {
    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(UTF8_SPAN)))
        .isEqualTo(UTF8_SPAN);
  }

  @Test void falseOnEmpty_inputSpans_JSON_V1() {
    assertThat(SpanBytesDecoder.JSON_V1.decodeList(new byte[0], new ArrayList<>())).isFalse();
  }

  @Test void falseOnEmpty_inputSpans_THRIFT() {
    assertThat(SpanBytesDecoder.THRIFT.decodeList(new byte[0], new ArrayList<>())).isFalse();
  }

  /**
   * Particular, thrift can mistake malformed content as a huge list. Let's not blow up.
   */
  @Test void niceErrorOnMalformed_inputSpans_JSON_V1() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      SpanBytesDecoder.JSON_V1.decodeList(new byte[] {'h', 'e', 'l', 'l', 'o'});
    });
    assertTrue(exception.getMessage().contains("Malformed reading List<Span> from "));
  }

  @Test void niceErrorOnMalformed_inputSpans_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      SpanBytesDecoder.THRIFT.decodeList(new byte[] {'h', 'e', 'l', 'l', 'o'});
    });
    assertTrue(exception.getMessage()
      .contains("Truncated: length 1 > bytes available 0 reading List<Span> from TBinary"));
  }

  @Test void traceRoundTrip_JSON_V1() {
    byte[] message = SpanBytesEncoder.JSON_V1.encodeList(TRACE);

    assertThat(SpanBytesDecoder.JSON_V1.decodeList(message)).isEqualTo(TRACE);
  }

  @Test void traceRoundTrip_THRIFT() {
    byte[] message = SpanBytesEncoder.THRIFT.encodeList(TRACE);

    assertThat(SpanBytesDecoder.THRIFT.decodeList(message)).isEqualTo(TRACE);
  }

  @Test void spansRoundTrip_JSON_V1() {
    List<Span> tenClientSpans = Collections.nCopies(10, span);

    byte[] message = SpanBytesEncoder.JSON_V1.encodeList(tenClientSpans);

    assertThat(SpanBytesDecoder.JSON_V1.decodeList(message)).isEqualTo(tenClientSpans);
  }

  @Test void spansRoundTrip_THRIFT() {
    List<Span> tenClientSpans = Collections.nCopies(10, span);

    byte[] message = SpanBytesEncoder.THRIFT.encodeList(tenClientSpans);

    assertThat(SpanBytesDecoder.THRIFT.decodeList(message)).isEqualTo(tenClientSpans);
  }

  @Test void spanRoundTrip_noRemoteServiceName_JSON_V1() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noRemoteServiceName_THRIFT() {
    span = span.toBuilder().remoteEndpoint(BACKEND.toBuilder().serviceName(null).build()).build();

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_JSON_V1() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_THRIFT() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN;

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_incomplete_JSON_V1() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_incomplete_THRIFT() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().duration(null).build();

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_shared_JSON_V1() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(SpanBytesEncoder.JSON_V1.encode(span)))
      .isEqualTo(span);
  }

  @Test void spanRoundTrip_noAnnotations_rootServerSpan_shared_THRIFT() {
    span = NO_ANNOTATIONS_ROOT_SERVER_SPAN.toBuilder().shared(true).build();

    assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(span)))
      .isEqualTo(span);
  }

  @Test
  @Disabled
  void niceErrorOnUppercase_traceId_JSON_V1() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": \"48485A3953BB6124\",\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": \"6b221d5bc9e6496c\"\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(exception.getMessage()
      .contains("48485A3953BB6124 should be lower-hex encoded with no prefix"));
  }

  @Test void readsTraceIdHighFromTraceIdField() {
    byte[] with128BitTraceId =
      ("{\n"
        + "  \"traceId\": \"48485a3953bb61246b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}")
        .getBytes(UTF_8);
    byte[] withLower64bitsTraceId =
      ("{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"name\": \"get-traces\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\"\n"
        + "}")
        .getBytes(UTF_8);

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(with128BitTraceId))
      .isEqualTo(
        SpanBytesDecoder.JSON_V1
          .decodeOne(withLower64bitsTraceId)
          .toBuilder()
          .traceId("48485a3953bb61246b221d5bc9e6496c")
          .build());
  }

  @Test void ignoresNull_topLevelFields() {
    String json =
        "{\n"
            + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
            + "  \"parentId\": null,\n"
            + "  \"id\": \"6b221d5bc9e6496c\",\n"
            + "  \"name\": null,\n"
            + "  \"timestamp\": null,\n"
            + "  \"duration\": null,\n"
            + "  \"annotations\": null,\n"
            + "  \"binaryAnnotations\": null,\n"
            + "  \"debug\": null,\n"
            + "  \"shared\": null\n"
            + "}";

    SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
  }

  @Test void ignoresNull_endpoint_topLevelFields() {
    String json =
        "{\n"
            + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
            + "  \"id\": \"6b221d5bc9e6496c\",\n"
            + "  \"binaryAnnotations\": [\n"
            + "    {\n"
            + "      \"key\": \"lc\",\n"
            + "      \"value\": \"\",\n"
            + "      \"endpoint\": {\n"
            + "        \"serviceName\": null,\n"
            + "    \"ipv4\": \"127.0.0.1\",\n"
            + "        \"ipv6\": null,\n"
            + "        \"port\": null\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint())
        .isEqualTo(Endpoint.newBuilder().ip("127.0.0.1").build());
  }

  @Test void skipsIncompleteEndpoint() {
    String json =
        "{\n"
            + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
            + "  \"id\": \"6b221d5bc9e6496c\",\n"
            + "  \"binaryAnnotations\": [\n"
            + "    {\n"
            + "      \"key\": \"lc\",\n"
            + "      \"value\": \"\",\n"
            + "      \"endpoint\": {\n"
            + "        \"serviceName\": null,\n"
            + "        \"ipv4\": null,\n"
            + "        \"ipv6\": null,\n"
            + "        \"port\": null\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint()).isNull();
    json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"lc\",\n"
        + "      \"value\": \"\",\n"
        + "      \"endpoint\": {\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint()).isNull();
  }

  @Test void ignoresNonAddressBooleanBinaryAnnotations() {
    String json =
      "{\n"
        + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
        + "  \"id\": \"6b221d5bc9e6496c\",\n"
        + "  \"binaryAnnotations\": [\n"
        + "    {\n"
        + "      \"key\": \"aa\",\n"
        + "      \"value\": true,\n"
        + "      \"endpoint\": {\n"
        + "        \"serviceName\": \"foo\"\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    Span decoded = SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    assertThat(decoded.tags()).isEmpty();
    assertThat(decoded.localEndpoint()).isNull();
    assertThat(decoded.remoteEndpoint()).isNull();
  }

  @Test void niceErrorOnIncomplete_annotation() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": \"6b221d5bc9e6496c\",\n"
          + "  \"annotations\": [\n"
          + "    { \"timestamp\": 1472470996199000}\n"
          + "  ]\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(
      exception.getMessage().contains("Incomplete annotation at $.annotations[0].timestamp"));
  }

  @Test void niceErrorOnNull_traceId() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": null,\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": \"6b221d5bc9e6496c\"\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(exception.getMessage().contains("Expected a string but was NULL"));
  }

  @Test void niceErrorOnNull_id() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": null\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(exception.getMessage().contains("Expected a string but was NULL"));
  }

  @Test void niceErrorOnNull_annotationValue() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": \"6b221d5bc9e6496c\",\n"
          + "  \"annotations\": [\n"
          + "    { \"timestamp\": 1472470996199000, \"value\": NULL}\n"
          + "  ]\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(exception.getMessage().contains("$.annotations[0].value"));
  }

  @Test void niceErrorOnNull_annotationTimestamp() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        "{\n"
          + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
          + "  \"name\": \"get-traces\",\n"
          + "  \"id\": \"6b221d5bc9e6496c\",\n"
          + "  \"annotations\": [\n"
          + "    { \"timestamp\": NULL, \"value\": \"foo\"}\n"
          + "  ]\n"
          + "}";

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertTrue(exception.getMessage().contains("$.annotations[0].timestamp"));
  }

  @Test void readSpan_localEndpoint_noServiceName() {
    String json =
        "{\n"
            + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
            + "  \"name\": \"get-traces\",\n"
            + "  \"id\": \"6b221d5bc9e6496c\",\n"
            + "  \"localEndpoint\": {\n"
            + "    \"ipv4\": \"127.0.0.1\"\n"
            + "  }\n"
            + "}";

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localServiceName())
        .isNull();
  }

  @Test void readSpan_remoteEndpoint_noServiceName() {
    String json =
        "{\n"
            + "  \"traceId\": \"6b221d5bc9e6496c\",\n"
            + "  \"name\": \"get-traces\",\n"
            + "  \"id\": \"6b221d5bc9e6496c\",\n"
            + "  \"remoteEndpoint\": {\n"
            + "    \"ipv4\": \"127.0.0.1\"\n"
            + "  }\n"
            + "}";

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).remoteServiceName())
        .isNull();
  }
}
