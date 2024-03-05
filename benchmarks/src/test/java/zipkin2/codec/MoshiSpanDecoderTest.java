/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TRACE;

public class MoshiSpanDecoderTest {
  byte[] encoded = SpanBytesEncoder.JSON_V2.encodeList(TRACE);

  @Test void decodeList_bytes() {
    assertThat(new MoshiSpanDecoder().decodeList(encoded))
      .isEqualTo(TRACE);
  }

  @Test void decodeList_byteBuffer() {
    ByteBuf encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encoded.length);
    encodedBuf.writeBytes(encoded);
    try {
      assertThat(new MoshiSpanDecoder().decodeList(encodedBuf.nioBuffer()))
        .isEqualTo(TRACE);
    } finally {
      encodedBuf.release();
    }
  }
}
