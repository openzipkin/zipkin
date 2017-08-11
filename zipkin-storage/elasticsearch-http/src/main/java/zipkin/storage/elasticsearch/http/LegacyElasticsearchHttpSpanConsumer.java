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

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import okio.Buffer;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.storage.Callback;

import static zipkin.internal.Util.UTF_8;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SPAN;

/**
 * This is the legacy implementation of our span consumer, which notably uses multi-type indexing
 * and the span v1 model. Multi-type indexing isn't supported on Elasticsearch 6.x. Moreover, the
 * span v1 model needs nested queries to access service names. This is expensive and requires a
 * separate type "servicespan" to make performant.
 */
// TODO: remove when we stop writing span1 format
class LegacyElasticsearchHttpSpanConsumer extends ElasticsearchHttpSpanConsumer {

  LegacyElasticsearchHttpSpanConsumer(ElasticsearchHttpStorage es) {
    super(es);
  }

  @Override MultiTypeBulkSpanIndexer newBulkSpanIndexer(ElasticsearchHttpStorage es) {
    return new MultiTypeBulkSpanIndexer(es);
  }

  static class MultiTypeBulkSpanIndexer extends BulkSpanIndexer {
    Map<String, Set<Pair<String>>> indexToServiceSpans = new LinkedHashMap<>();

    MultiTypeBulkSpanIndexer(ElasticsearchHttpStorage es) {
      super(es);
    }

    @Override void add(long indexTimestamp, Span span, @Nullable Long spanTimestamp) {
      String type = null; // multi-type index: span isn't a parameter to the index name
      String index = indexNameFormatter.formatTypeAndTimestamp(type, indexTimestamp);
      if (!span.name.isEmpty()) putServiceSpans(indexToServiceSpans, index, span);
      byte[] document = Codec.JSON.writeSpan(span);
      if (spanTimestamp != null) document = prefixWithTimestampMillis(document, spanTimestamp);
      indexer.add(index, SPAN, document, null /* Allow ES to choose an ID */);
    }

    void putServiceSpans(Map<String, Set<Pair<String>>> indexToServiceSpans, String index, Span s) {
      Set<Pair<String>> serviceSpans = indexToServiceSpans.get(index);
      if (serviceSpans == null) {
        indexToServiceSpans.put(index, serviceSpans = new LinkedHashSet<>());
      }
      for (String serviceName : s.serviceNames()) {
        serviceSpans.add(Pair.create(serviceName, s.name));
      }
    }

    /**
     * Adds service and span names to the pending batch. The id is "serviceName|spanName" to prevent
     * a large order of duplicates ending up in the daily index. This also means queries do not need
     * to deduplicate.
     */
    @Override void execute(Callback<Void> callback) throws IOException {
      if (indexToServiceSpans.isEmpty()) {
        indexer.execute(callback);
        return;
      }
      Buffer buffer = new Buffer();
      for (Map.Entry<String, Set<Pair<String>>> entry : indexToServiceSpans.entrySet()) {
        String index = entry.getKey();
        for (Pair<String> serviceSpan : entry.getValue()) {
          JsonWriter writer = JsonWriter.of(buffer);
          writer.beginObject();
          writer.name("serviceName").value(serviceSpan._1);
          writer.name("spanName").value(serviceSpan._2);
          writer.endObject();
          byte[] document = buffer.readByteArray();
          indexer.add(index, "servicespan", document, serviceSpan._1 + "|" + serviceSpan._2);
        }
      }
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
