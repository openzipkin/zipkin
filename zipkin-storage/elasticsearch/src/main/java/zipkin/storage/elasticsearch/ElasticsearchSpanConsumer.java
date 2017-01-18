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
package zipkin.storage.elasticsearch;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;
import zipkin.storage.elasticsearch.InternalElasticsearchClient.BulkSpanIndexer;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.propagateIfFatal;

final class ElasticsearchSpanConsumer implements AsyncSpanConsumer {
  private static final byte[] TIMESTAMP_MILLIS_PREFIX = "{\"timestamp_millis\":".getBytes(UTF_8);

  private final InternalElasticsearchClient client;
  private final IndexNameFormatter indexNameFormatter;

  ElasticsearchSpanConsumer(InternalElasticsearchClient client,
      IndexNameFormatter indexNameFormatter) {
    this.client = client;
    this.indexNameFormatter = indexNameFormatter;
  }

  @Override public void accept(List<Span> spans, Callback<Void> callback) {
    if (spans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }
    try {
      indexSpans(client.bulkSpanIndexer(), spans).execute(callback);
    } catch (Throwable t) {
      propagateIfFatal(t);
      callback.onError(t);
    }
  }

  BulkSpanIndexer indexSpans(BulkSpanIndexer indexer, List<Span> spans) throws IOException {
    for (Span span : spans) {
      Long timestamp = guessTimestamp(span);
      Long timestampMillis;
      String index; // which index to store this span into
      if (timestamp != null) {
        timestampMillis = TimeUnit.MICROSECONDS.toMillis(timestamp);
        index = indexNameFormatter.indexNameForTimestamp(timestampMillis);
      } else {
        timestampMillis = null;
        index = indexNameFormatter.indexNameForTimestamp(System.currentTimeMillis());
      }
      indexer.add(index, span, timestampMillis);
    }
    return indexer;
  }

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
