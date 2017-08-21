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
package zipkin.internal.v2.codec;

import java.util.List;

/**
 * Senders like Kafka use byte[] message encoding. This provides helpers to concatenate spans into a
 * list.
 */
public interface MessageEncoder<M> {
  MessageEncoder<byte[]> JSON_BYTES = new MessageEncoder<byte[]>() {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public byte[] encode(List<byte[]> values) {
      byte[] buf = new byte[encoding().listSizeInBytes(values)];
      int pos = 0;
      buf[pos++] = '[';
      for (int i = 0, length = values.size(); i < length; ) {
        byte[] v = values.get(i++);
        System.arraycopy(v, 0, buf, pos, v.length);
        pos += v.length;
        if (i < length) buf[pos++] = ',';
      }
      buf[pos] = ']';
      return buf;
    }
  };

  Encoding encoding();

  /**
   * Combines a list of encoded spans into an encoded list. For example, in json, this would be
   * comma-separated and enclosed by brackets.
   *
   * <p>The primary use of this is batch reporting spans. For example, spans are {@link
   * Encoder#encode(Object) encoded} one-by-one into a queue. This queue is drained up to a byte
   * threshold. Then, the list is encoded with this function and reported out-of-process.
   */
  M encode(List<byte[]> encodedSpans);
}
