/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.TRACE;

public class JacksonSpanDecoderTest {
  byte[] encoded = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
  byte[] encodedSpan = SpanBytesEncoder.JSON_V2.encode(CLIENT_SPAN);

  @Test public void decodeList_bytes() {
    assertThat(JacksonSpanDecoder.decodeList(encoded))
      .isEqualTo(TRACE);
  }

  @Test public void decodeList_byteBuffer() {
    ByteBuf encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encoded.length);
    encodedBuf.writeBytes(encoded);
    try {
      assertThat(JacksonSpanDecoder.decodeList(encodedBuf.nioBuffer()))
        .isEqualTo(TRACE);
    } finally {
      encodedBuf.release();
    }
  }

  @Test public void decodeOne() {
    assertThat(JacksonSpanDecoder.decodeOne(encodedSpan))
      .isEqualTo(CLIENT_SPAN);
  }

  @Test public void decodeOne_byteBuffer() {
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
