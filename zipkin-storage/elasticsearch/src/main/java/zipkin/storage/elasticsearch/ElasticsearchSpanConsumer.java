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
package zipkin.storage.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.Span;
import zipkin.storage.elasticsearch.InternalElasticsearchClient.BulkSpanIndexer;
import zipkin.storage.guava.GuavaSpanConsumer;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;

final class ElasticsearchSpanConsumer implements GuavaSpanConsumer {
  private static final byte[] COLLECTOR_TIMESTAMP_MILLIS_PREFIX =
      "{\"collector_timestamp_millis\":".getBytes();
  private static final byte[] TIMESTAMP_MILLIS_PREFIX = "{\"timestamp_millis\":".getBytes();
  private static final ListenableFuture<Void> VOID = immediateFuture(null);

  private final InternalElasticsearchClient client;
  private final IndexNameFormatter indexNameFormatter;

  ElasticsearchSpanConsumer(InternalElasticsearchClient client,
      IndexNameFormatter indexNameFormatter) {
    this.client = client;
    this.indexNameFormatter = indexNameFormatter;
  }

  @Override public ListenableFuture<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return VOID;
    try {
      return indexSpans(client.bulkSpanIndexer(), spans).execute();
    } catch (Exception e) {
      return Futures.immediateFailedFuture(e);
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
   * In order to allow systems like Kibana to search by timestamp, we add "timestamp_millis" to the
   * span. In order to help people understand transport lag, we add "collector_timestamp_millis".
   * The cheapest way to do this without changing the codec is prefixing it to the json.
   *
   * <p>
   * {"traceId":"... -> {"timestamp_millis":12345,"collector_timestamp_millis":12346,"traceId":"...
   *
   * @param span authoritative {@link Span#timestamp} truncated to milliseconds
   * @param now value of {@link System#currentTimeMillis()}
   */
  @VisibleForTesting
  static byte[] prefixWithTimestamps(byte[] input, Long span, long now) {
    String nowAsString = Long.toString(now);
    int length = COLLECTOR_TIMESTAMP_MILLIS_PREFIX.length + nowAsString.length() + input.length;

    int pos = 0;
    byte[] result;
    if (span != null) {
      String spanAsString = Long.toString(span);
      length += TIMESTAMP_MILLIS_PREFIX.length + spanAsString.length();
      result = new byte[length];
      pos = foo(TIMESTAMP_MILLIS_PREFIX, 0, spanAsString, result, pos);
      pos = foo(COLLECTOR_TIMESTAMP_MILLIS_PREFIX, 1, nowAsString, result, pos);
    } else {
      result = new byte[length];
      pos = foo(COLLECTOR_TIMESTAMP_MILLIS_PREFIX, 0, nowAsString, result, pos);
    }
    // starting at position 1 discards the old head of '{'
    System.arraycopy(input, 1, result, pos, input.length - 1);
    return result;
  }

  static int foo(byte[] bytes, int bytesPos, String ascii, byte[] dest, int destPos) {
    int bytesLength = bytes.length - bytesPos;
    System.arraycopy(bytes, bytesPos, dest, destPos, bytesLength);
    destPos += bytesLength;
    for (int i = 0, length = ascii.length(); i < length; i++) {
      dest[destPos++] = (byte) ascii.charAt(i);
    }
    dest[destPos++] = ',';
    return destPos;
  }
}
