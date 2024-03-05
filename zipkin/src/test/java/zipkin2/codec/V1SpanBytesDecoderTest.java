/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.codec.SpanBytesEncoderTest.LOCAL_SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.NO_ANNOTATIONS_ROOT_SERVER_SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.SPAN;
import static zipkin2.codec.SpanBytesEncoderTest.UTF8_SPAN;

/** V1 tests for {@link SpanBytesDecoderTest} */
class V1SpanBytesDecoderTest {
  Span span = SPAN;

  @Test void niceErrorOnTruncatedSpans_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      byte[] encoded = SpanBytesEncoder.THRIFT.encodeList(TRACE);
      SpanBytesDecoder.THRIFT.decodeList(Arrays.copyOfRange(encoded, 0, 10));
    });
    assertThat(exception.getMessage()).contains("Truncated: length 8 > bytes available 2 reading List<Span> from TBinary");
  }

  @Test void niceErrorOnTruncatedSpan_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      byte[] encoded = SpanBytesEncoder.THRIFT.encode(SPAN);
      SpanBytesDecoder.THRIFT.decodeOne(Arrays.copyOfRange(encoded, 0, 10));
    });
    assertThat(exception.getMessage()).contains("Truncated: length 8 > bytes available 7 reading Span from TBinary");
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
    assertThat(exception.getMessage()).contains("Malformed reading List<Span> from ");
  }

  @Test void niceErrorOnMalformed_inputSpans_THRIFT() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      SpanBytesDecoder.THRIFT.decodeList(new byte[] {'h', 'e', 'l', 'l', 'o'});
    });
    assertThat(exception.getMessage()).contains("Truncated: length 1 > bytes available 0 reading List<Span> from TBinary");
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
        """
        {
          "traceId": "48485A3953BB6124",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("48485A3953BB6124 should be lower-hex encoded with no prefix");
  }

  @Test void readsTraceIdHighFromTraceIdField() {
    byte[] with128BitTraceId =
      ("""
        {
          "traceId": "48485a3953bb61246b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """)
        .getBytes(UTF_8);
    byte[] withLower64bitsTraceId =
      ("""
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """)
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
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "parentId": null,
          "id": "6b221d5bc9e6496c",
          "name": null,
          "timestamp": null,
          "duration": null,
          "annotations": null,
          "binaryAnnotations": null,
          "debug": null,
          "shared": null
        }
        """;

    SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
  }

  @Test void ignoresNull_endpoint_topLevelFields() {
    String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "id": "6b221d5bc9e6496c",
          "binaryAnnotations": [
            {
              "key": "lc",
              "value": "",
              "endpoint": {
                "serviceName": null,
            "ipv4": "127.0.0.1",
                "ipv6": null,
                "port": null
              }
            }
          ]
        }
        """;

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint())
        .isEqualTo(Endpoint.newBuilder().ip("127.0.0.1").build());
  }

  @Test void skipsIncompleteEndpoint() {
    String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "id": "6b221d5bc9e6496c",
          "binaryAnnotations": [
            {
              "key": "lc",
              "value": "",
              "endpoint": {
                "serviceName": null,
                "ipv4": null,
                "ipv6": null,
                "port": null
              }
            }
          ]
        }
        """;
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint()).isNull();
    json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "id": "6b221d5bc9e6496c",
        "binaryAnnotations": [
          {
            "key": "lc",
            "value": "",
            "endpoint": {
            }
          }
        ]
      }
      """;
    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localEndpoint()).isNull();
  }

  @Test void ignoresNonAddressBooleanBinaryAnnotations() {
    String json =
      """
      {
        "traceId": "6b221d5bc9e6496c",
        "id": "6b221d5bc9e6496c",
        "binaryAnnotations": [
          {
            "key": "aa",
            "value": true,
            "endpoint": {
              "serviceName": "foo"
            }
          }
        ]
      }
      """;

    Span decoded = SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    assertThat(decoded.tags()).isEmpty();
    assertThat(decoded.localEndpoint()).isNull();
    assertThat(decoded.remoteEndpoint()).isNull();
  }

  @Test void niceErrorOnIncomplete_annotation() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c",
          "annotations": [
            { "timestamp": 1472470996199000}
          ]
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("Incomplete annotation at $.annotations[0].timestamp");
  }

  @Test void niceErrorOnNull_traceId() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        """
        {
          "traceId": null,
          "name": "get-traces",
          "id": "6b221d5bc9e6496c"
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("Expected a string but was NULL");
  }

  @Test void niceErrorOnNull_id() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": null
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("Expected a string but was NULL");
  }

  @Test void niceErrorOnNull_annotationValue() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c",
          "annotations": [
            { "timestamp": 1472470996199000, "value": NULL}
          ]
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("$.annotations[0].value");
  }

  @Test void niceErrorOnNull_annotationTimestamp() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

      String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c",
          "annotations": [
            { "timestamp": NULL, "value": "foo"}
          ]
        }
        """;

      SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8));
    });
    assertThat(exception.getMessage()).contains("$.annotations[0].timestamp");
  }

  @Test void readSpan_localEndpoint_noServiceName() {
    String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c",
          "localEndpoint": {
            "ipv4": "127.0.0.1"
          }
        }
        """;

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).localServiceName())
        .isNull();
  }

  @Test void readSpan_remoteEndpoint_noServiceName() {
    String json =
        """
        {
          "traceId": "6b221d5bc9e6496c",
          "name": "get-traces",
          "id": "6b221d5bc9e6496c",
          "remoteEndpoint": {
            "ipv4": "127.0.0.1"
          }
        }
        """;

    assertThat(SpanBytesDecoder.JSON_V1.decodeOne(json.getBytes(UTF_8)).remoteServiceName())
        .isNull();
  }
}
