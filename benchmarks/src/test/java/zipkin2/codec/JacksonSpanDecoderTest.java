/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.TRACE;

public class JacksonSpanDecoderTest {
  byte[] encoded = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
  byte[] encodedSpan = SpanBytesEncoder.JSON_V2.encode(CLIENT_SPAN);

  @Test void decodeList_bytes() {
    assertThat(JacksonSpanDecoder.decodeList(encoded))
      .isEqualTo(TRACE);
  }

  @Test void decodeList_byteBuffer() {
    ByteBuf encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encoded.length);
    encodedBuf.writeBytes(encoded);
    try {
      assertThat(JacksonSpanDecoder.decodeList(encodedBuf.nioBuffer()))
        .isEqualTo(TRACE);
    } finally {
      encodedBuf.release();
    }
  }

  @Test void decodeOne() {
    assertThat(JacksonSpanDecoder.decodeOne(encodedSpan))
      .isEqualTo(CLIENT_SPAN);
  }

  @Test void decodeOne_byteBuffer() {
    ByteBuf encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encodedSpan.length);
    encodedBuf.writeBytes(encodedSpan);
    try {
      assertThat(JacksonSpanDecoder.decodeOne(encodedBuf.nioBuffer()))
        .isEqualTo(CLIENT_SPAN);
    } finally {
      encodedBuf.release();
    }
  }
}
