/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static zipkin2.TestObjects.FRONTEND;

class SpanBytesDecoderDetectorTest {
  Span span1 =
      Span.newBuilder()
          .traceId("a")
          .id("b")
          .name("get")
          .timestamp(10)
          .duration(30)
          .kind(Span.Kind.SERVER)
          .shared(true)
          .putTag("http.method", "GET")
          .localEndpoint(FRONTEND)
          .build();
  Span span2 =
      Span.newBuilder()
          .traceId("a")
          .parentId("b")
          .id("c")
          .name("get")
          .timestamp(15)
          .duration(10)
          .localEndpoint(FRONTEND)
          .build();

  @Test void decoderForMessage_json_v1() {
    byte[] message = SpanBytesEncoder.JSON_V1.encode(span1);
    assertThat(SpanBytesDecoderDetector.decoderForMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V1);
  }

  @Test void decoderForMessage_json_v1_list() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.JSON_V1.encodeList(List.of(span1, span2));
      SpanBytesDecoderDetector.decoderForMessage(message);
    });
  }

  @Test void decoderForListMessage_json_v1() {
    byte[] message = SpanBytesEncoder.JSON_V1.encodeList(List.of(span1, span2));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V1);
  }

  @Test void decoderForListMessage_json_v1_singleItem() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.JSON_V1.encode(span1);
      SpanBytesDecoderDetector.decoderForListMessage(message);
    });
  }

  /** Single-element reads were for legacy non-list encoding. Don't add new code that does this */
  @Test void decoderForMessage_json_v2() {
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.JSON_V2.encode(span1);
      assertThat(SpanBytesDecoderDetector.decoderForMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V2);
    });
  }

  @Test void decoderForMessage_json_v2_list() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.JSON_V2.encodeList(List.of(span1, span2));
      SpanBytesDecoderDetector.decoderForMessage(message);
    });
  }

  @Test void decoderForListMessage_json_v2() {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(List.of(span1, span2));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V2);
  }

  @Test void decoderForListMessage_json_v2_partial_localEndpoint() {
    Span span =
        Span.newBuilder()
            .traceId("a")
            .id("b")
            .localEndpoint(Endpoint.newBuilder().serviceName("foo").build())
            .build();

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(List.of(span));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V2);
  }

  @Test void decoderForListMessage_json_v2_partial_remoteEndpoint() {
    Span span =
        Span.newBuilder()
            .traceId("a")
            .id("b")
            .kind(Span.Kind.CLIENT)
            .remoteEndpoint(Endpoint.newBuilder().serviceName("foo").build())
            .build();

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(List.of(span));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V2);
  }

  @Test void decoderForListMessage_json_v2_partial_tag() {
    Span span = Span.newBuilder().traceId("a").id("b").putTag("foo", "bar").build();

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(List.of(span));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.JSON_V2);
  }

  @Test void decoderForListMessage_json_v2_singleItem() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.JSON_V2.encode(span1);
      SpanBytesDecoderDetector.decoderForListMessage(message);
    });
  }

  @Test void decoderForMessage_thrift() {
    byte[] message = SpanBytesEncoder.THRIFT.encode(span1);
    assertThat(SpanBytesDecoderDetector.decoderForMessage(message))
        .isEqualTo(SpanBytesDecoder.THRIFT);
  }

  @Test void decoderForMessage_thrift_list() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.THRIFT.encodeList(List.of(span1, span2));
      SpanBytesDecoderDetector.decoderForMessage(message);
    });
  }

  @Test void decoderForListMessage_thrift() {
    byte[] message = SpanBytesEncoder.THRIFT.encodeList(List.of(span1, span2));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.THRIFT);
  }

  /**
   * We encoded incorrectly for years, so we have to read this data eventhough it is wrong.
   *
   * <p>See openzipkin/zipkin-reporter-java#133
   */
  @Test void decoderForListMessage_thrift_incorrectFirstByte() {
    byte[] message = SpanBytesEncoder.THRIFT.encodeList(List.of(span1, span2));
    message[0] = 11; // We made a typo.. it should have been 12
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
      .isEqualTo(SpanBytesDecoder.THRIFT);
  }

  @Test void decoderForListMessage_thrift_singleItem() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.THRIFT.encode(span1);
      SpanBytesDecoderDetector.decoderForListMessage(message);
    });
  }

  /**
   * Single-element reads were for legacy non-list encoding. Don't add new code that does this
   */
  @Test void decoderForMessage_proto3() {
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.PROTO3.encode(span1);
      assertThat(SpanBytesDecoderDetector.decoderForMessage(message))
        .isEqualTo(SpanBytesDecoder.PROTO3);
    });
  }

  @Test void decoderForMessage_proto3_list() {
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
      byte[] message = SpanBytesEncoder.PROTO3.encodeList(List.of(span1, span2));
      SpanBytesDecoderDetector.decoderForMessage(message);
    });
  }

  @Test void decoderForListMessage_proto3() {
    byte[] message = SpanBytesEncoder.PROTO3.encodeList(List.of(span1, span2));
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.PROTO3);
  }

  /** There is no difference between a list of size one and a single element in proto3 */
  @Test void decoderForListMessage_proto3_singleItem() {
    byte[] message = SpanBytesEncoder.PROTO3.encode(span1);
    assertThat(SpanBytesDecoderDetector.decoderForListMessage(message))
        .isEqualTo(SpanBytesDecoder.PROTO3);
  }

  @Test void decoderForMessage_unknown() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      SpanBytesDecoderDetector.decoderForMessage(new byte[]{'h'});
    });
  }

  @Test void decoderForListMessage_unknown() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
      SpanBytesDecoderDetector.decoderForListMessage(new byte[]{'h'});
    });
  }
}
