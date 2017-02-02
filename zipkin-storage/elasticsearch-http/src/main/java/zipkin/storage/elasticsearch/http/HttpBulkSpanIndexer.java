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
package zipkin.storage.elasticsearch.http;

import zipkin.Codec;
import zipkin.Span;

import static zipkin.internal.Util.UTF_8;

final class HttpBulkSpanIndexer extends HttpBulkIndexer<Span> {

  HttpBulkSpanIndexer(ElasticsearchHttpStorage es) {
    super("span", es);
  }

  /**
   * In order to allow systems like Kibana to search by timestamp, we add a field
   * "timestamp_millis" when storing a span that has a timestamp. The cheapest way to do this
   * without changing the codec is prefixing it to the json.
   *
   * <p>For example. {"traceId":".. becomes {"timestamp_millis":12345,"traceId":"...
   */
  HttpBulkSpanIndexer add(String index, Span span, Long timestampMillis) {
    String id = null; // Allow ES to choose an ID
    if (timestampMillis == null) {
      super.add(index, span, id);
      return this;
    }
    writeIndexMetadata(index, id);
    if (timestampMillis != null) {
      body.write(prefixWithTimestampMillis(toJsonBytes(span), timestampMillis));
    } else {
      body.write(toJsonBytes(span));
    }
    body.writeByte('\n');

    if (flushOnWrites) indices.add(index);
    return this;
  }

  @Override byte[] toJsonBytes(Span span) {
    return Codec.JSON.writeSpan(span);
  }

  private static final byte[] TIMESTAMP_MILLIS_PREFIX = "{\"timestamp_millis\":".getBytes(UTF_8);

  /**
   * In order to allow systems like Kibana to search by timestamp, we add a field "timestamp_millis"
   * when storing. The cheapest way to do this without changing the codec is prefixing it to the
   * json. For example. {"traceId":"... becomes {"timestamp_millis":12345,"traceId":"...
   */
  static byte[] prefixWithTimestampMillis(byte[] input, long timestampMillis) {
    String dateAsString = Long.toString(timestampMillis);
    byte[] newSpanBytes =
        new byte[TIMESTAMP_MILLIS_PREFIX.length + dateAsString.length() + input.length];
    int pos = 0;
    System.arraycopy(TIMESTAMP_MILLIS_PREFIX, 0, newSpanBytes, pos, TIMESTAMP_MILLIS_PREFIX.length);
    pos += TIMESTAMP_MILLIS_PREFIX.length;
    for (int i = 0, length = dateAsString.length(); i < length; i++) {
      newSpanBytes[pos++] = (byte) dateAsString.charAt(i);
    }
    newSpanBytes[pos++] = ',';
    // starting at position 1 discards the old head of '{'
    System.arraycopy(input, 1, newSpanBytes, pos, input.length - 1);
    return newSpanBytes;
  }
}
