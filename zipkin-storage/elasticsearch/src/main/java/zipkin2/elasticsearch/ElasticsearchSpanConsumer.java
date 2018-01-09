/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.elasticsearch;

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.Buffer;
import okio.ByteString;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.elasticsearch.internal.HttpBulkIndexer;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.storage.SpanConsumer;

class ElasticsearchSpanConsumer implements SpanConsumer { // not final for testing
  static final Logger LOG = Logger.getLogger(ElasticsearchSpanConsumer.class.getName());

  final ElasticsearchStorage es;
  final IndexNameFormatter indexNameFormatter;
  final boolean searchEnabled;

  ElasticsearchSpanConsumer(ElasticsearchStorage es) {
    this.es = es;
    this.indexNameFormatter = es.indexNameFormatter();
    this.searchEnabled = es.searchEnabled();
  }

  @Override public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    BulkSpanIndexer indexer = new BulkSpanIndexer(es);
    indexSpans(indexer, spans);
    return indexer.newCall();
  }

  void indexSpans(BulkSpanIndexer indexer, List<Span> spans) {
    for (Span span : spans) {
      long spanTimestamp = span.timestampAsLong();
      long indexTimestamp = 0L; // which index to store this span into
      if (spanTimestamp != 0L) {
        indexTimestamp = spanTimestamp = TimeUnit.MICROSECONDS.toMillis(spanTimestamp);
      } else {
        // guessTimestamp is made for determining the span's authoritative timestamp. When choosing
        // the index bucket, any annotation is better than using current time.
        for (int i = 0, length = span.annotations().size(); i < length; i++) {
          indexTimestamp = span.annotations().get(i).timestamp() / 1000;
          break;
        }
        if (indexTimestamp == 0L) indexTimestamp = System.currentTimeMillis();
      }
      indexer.add(indexTimestamp, span, spanTimestamp);
    }
  }

  static final class BulkSpanIndexer {
    final HttpBulkIndexer indexer;
    final IndexNameFormatter indexNameFormatter;
    final boolean searchEnabled;

    BulkSpanIndexer(ElasticsearchStorage es) {
      this.indexer = new HttpBulkIndexer("index-span", es);
      this.indexNameFormatter = es.indexNameFormatter();
      this.searchEnabled = es.searchEnabled();
    }

    void add(long indexTimestamp, Span span, long timestampMillis) {
      String index = indexNameFormatter.formatTypeAndTimestamp(ElasticsearchSpanStore.SPAN, indexTimestamp);
      byte[] document = searchEnabled
        ? prefixWithTimestampMillisAndQuery(span, timestampMillis)
        : SpanBytesEncoder.JSON_V2.encode(span);
      indexer.add(index, ElasticsearchSpanStore.SPAN, document, null /* Allow ES to choose an ID */);
    }

    HttpCall<Void> newCall() {
      return indexer.newCall();
    }
  }

  /**
   * In order to allow systems like Kibana to search by timestamp, we add a field "timestamp_millis"
   * when storing. The cheapest way to do this without changing the codec is prefixing it to the
   * json. For example. {"traceId":"... becomes {"timestamp_millis":12345,"traceId":"...
   *
   * <p>Tags are stored as a dictionary. Since some tag names will include inconsistent number of
   * dots (ex "error" and perhaps "error.message"), we cannot index them naturally with
   * elasticsearch. Instead, we add an index-only (non-source) field of {@code _q} which includes
   * valid search queries. For example, the tag {@code error -> 500} results in {@code
   * "_q":["error", "error=500"]}. This matches the input query syntax, and can be checked manually
   * with curl.
   *
   * <p>Ex {@code curl -s localhost:9200/zipkin:span-2017-08-11/_search?q=_q:error=500}
   */
  static byte[] prefixWithTimestampMillisAndQuery(Span span, long timestampMillis) {
    Buffer query = new Buffer();
    JsonWriter writer = JsonWriter.of(query);
    try {
      writer.beginObject();

      if (timestampMillis != 0L) writer.name("timestamp_millis").value(timestampMillis);
      if (!span.tags().isEmpty() || !span.annotations().isEmpty()) {
        writer.name("_q");
        writer.beginArray();
        for (Annotation a : span.annotations()) {
          if (a.value().length() > 255) continue;
          writer.value(a.value());
        }
        for (Map.Entry<String, String> tag : span.tags().entrySet()) {
          if (tag.getKey().length() + tag.getValue().length() + 1 > 255) continue;
          writer.value(tag.getKey()); // search is possible by key alone
          writer.value(tag.getKey() + "=" + tag.getValue());
        }
        writer.endArray();
      }
      writer.endObject();
    } catch (IOException e) {
      // very unexpected to have an IOE for an in-memory write
      assert false : "Error indexing query for span: " + span;
      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "Error indexing query for span: " + span, e);
      }
      return SpanBytesEncoder.JSON_V2.encode(span);
    }
    byte[] document = SpanBytesEncoder.JSON_V2.encode(span);
    if (query.rangeEquals(0L, ByteString.of(new byte[] {'{', '}'}))) {
      return document;
    }
    byte[] prefix = query.readByteArray();

    byte[] newSpanBytes = new byte[prefix.length + document.length - 1];
    int pos = 0;
    System.arraycopy(prefix, 0, newSpanBytes, pos, prefix.length);
    pos += prefix.length;
    newSpanBytes[pos - 1] = ',';
    // starting at position 1 discards the old head of '{'
    System.arraycopy(document, 1, newSpanBytes, pos, document.length - 1);
    return newSpanBytes;
  }
}
