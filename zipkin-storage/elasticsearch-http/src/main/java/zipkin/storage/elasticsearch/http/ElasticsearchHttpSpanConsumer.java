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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.internal.Span2;
import zipkin.internal.Span2Codec;
import zipkin.internal.Span2Converter;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.propagateIfFatal;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SPAN;

class ElasticsearchHttpSpanConsumer implements AsyncSpanConsumer { // not final for testing

  final ElasticsearchHttpStorage es;
  final IndexNameFormatter indexNameFormatter;

  ElasticsearchHttpSpanConsumer(ElasticsearchHttpStorage es) {
    this.es = es;
    this.indexNameFormatter = es.indexNameFormatter();
  }

  @Override public void accept(List<Span> spans, Callback<Void> callback) {
    if (spans.isEmpty()) {
      callback.onSuccess(null);
      return;
    }
    try {
      BulkSpanIndexer indexer = newBulkSpanIndexer(es);
      indexSpans(indexer, spans);
      indexer.execute(callback);
    } catch (Throwable t) {
      propagateIfFatal(t);
      callback.onError(t);
    }
  }

  void indexSpans(BulkSpanIndexer indexer, List<Span> spans) throws IOException {
    for (Span span : spans) {
      Long timestamp = guessTimestamp(span);
      long indexTimestamp = 0L; // which index to store this span into
      Long spanTimestamp;
      if (timestamp != null) {
        indexTimestamp = spanTimestamp = TimeUnit.MICROSECONDS.toMillis(timestamp);
      } else {
        spanTimestamp = null;
        // guessTimestamp is made for determining the span's authoritative timestamp. When choosing
        // the index bucket, any annotation is better than using current time.
        for (int i = 0, length = span.annotations.size(); i < length; i++) {
          indexTimestamp = span.annotations.get(i).timestamp / 1000;
          break;
        }
        if (indexTimestamp == 0L) indexTimestamp = System.currentTimeMillis();
      }
      indexer.add(indexTimestamp, span, spanTimestamp);
    }
  }


  BulkSpanIndexer newBulkSpanIndexer(ElasticsearchHttpStorage es) {
    return new BulkSpanIndexer(es);
  }

  static class BulkSpanIndexer {
    final HttpBulkIndexer indexer;
    final IndexNameFormatter indexNameFormatter;

    BulkSpanIndexer(ElasticsearchHttpStorage es) {
      this.indexer = new HttpBulkIndexer("index-span", es);
      this.indexNameFormatter = es.indexNameFormatter();
    }

    void add(long indexTimestamp, Span span, @Nullable Long timestampMillis) {
      String index = indexNameFormatter.formatTypeAndTimestamp(SPAN, indexTimestamp);
      for (Span2 span2 : Span2Converter.fromSpan(span)) {
        byte[] document = Span2Codec.JSON.writeSpan(span2);
        if (timestampMillis != null) {
          document = prefixWithTimestampMillis(document, timestampMillis);
        }
        indexer.add(index, SPAN, document, null /* Allow ES to choose an ID */);
      }
    }

    void execute(Callback<Void> callback) throws IOException {
      indexer.execute(callback);
    }
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
