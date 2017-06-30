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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import okio.Buffer;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.Pair;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.propagateIfFatal;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.SERVICE_SPAN;

class ElasticsearchHttpSpanConsumer implements AsyncSpanConsumer { // not final for testing

  final ElasticsearchHttpStorage es;
  final IndexNameFormatter indexNameFormatter;
  private final static int MAX_CACHE_DAYS = 2;
  private final static int MAX_CACHE_ENTRIES = 1000;

  private final static Map<String, Set<Pair<String>>> indexToServiceSpansCache = new LinkedHashMap<String, Set<Pair<String>>>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Set<Pair<String>>> eldest) {
      return size() > MAX_CACHE_DAYS;
    }
  };
  private Map<String, Set<Pair<String>>> indexToServiceSpans = new LinkedHashMap<>();

  ElasticsearchHttpSpanConsumer(ElasticsearchHttpStorage es) {
    this.es = es;
    this.indexNameFormatter = es.indexNameFormatter();
  }

  @Override public void accept(List<Span> spans, Callback<Void> callback) {
    Callback<Void> callbackWrapper = new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {
            callback.onSuccess(value);
        }

        @Override
        public void onError(Throwable t) {
            clearIndexToServiceSpansCache(indexToServiceSpans, indexToServiceSpansCache);
            callback.onError(t);
        }
    };

    if (spans.isEmpty()) {
      callbackWrapper.onSuccess(null);
      return;
    }
    try {
      HttpBulkIndexer indexer = new HttpBulkIndexer("index-span", es);
      indexToServiceSpans = indexSpans(indexer, spans);

      // remove already cached items from indexToServiceSpans, add new items to cache
      for (Map.Entry<String, Set<Pair<String>>> entry : indexToServiceSpans.entrySet()) {
        Set<Pair<String>> serviceSpansEntryCached = null;
        synchronized(indexToServiceSpansCache) {
          serviceSpansEntryCached = indexToServiceSpansCache.get(entry.getKey());
        }
        if (serviceSpansEntryCached == null) {
          synchronized(indexToServiceSpansCache) {
            indexToServiceSpansCache.put(entry.getKey(), serviceSpansEntryCached = Collections.newSetFromMap(new LinkedHashMap<Pair<String>, Boolean>() {
              @Override
              protected boolean removeEldestEntry(Map.Entry<Pair<String>, Boolean> eldest) {
                return size() > MAX_CACHE_ENTRIES;
              }
            }));
          }
        }
        Set<Pair<String>> serviceSpansEntry = entry.getValue();
        synchronized(serviceSpansEntryCached) {
          serviceSpansEntry.removeAll(serviceSpansEntryCached);
          serviceSpansEntryCached.addAll(serviceSpansEntry);
        }
      }

      if (!indexToServiceSpans.isEmpty()) {
        indexNames(indexer, indexToServiceSpans);
      }
      indexer.execute(callbackWrapper);
    } catch (Throwable t) {
      propagateIfFatal(t);
      callbackWrapper.onError(t);
    }
  }
  /** Remove recently added items from servicespan cache */
  void clearIndexToServiceSpansCache(Map<String, Set<Pair<String>>> index, Map<String, Set<Pair<String>>> cache) {
    for (Map.Entry<String, Set<Pair<String>>> entry : index.entrySet()) {
      Set<Pair<String>> serviceSpansEntryCached = null;
      synchronized (cache) {
        serviceSpansEntryCached = cache.get(entry.getKey());
      }
      if (serviceSpansEntryCached == null || serviceSpansEntryCached.isEmpty()) continue;

      synchronized (serviceSpansEntryCached) {
        serviceSpansEntryCached.removeAll(entry.getValue());
      }
    }
  }

  /** Clear servicespan cache (for testing purposes) */
  void resetIndexToServiceSpansCache() {
    indexToServiceSpansCache.clear();
  }

  /** Indexes spans and returns a mapping of indexes that may need a names update */
  Map<String, Set<Pair<String>>> indexSpans(HttpBulkIndexer indexer, List<Span> spans) {
    Map<String, Set<Pair<String>>> indexToServiceSpans = new LinkedHashMap<>();
    for (Span span : spans) {
      Long timestamp = guessTimestamp(span);
      Long timestampMillis;
      String index; // which index to store this span into
      if (timestamp != null) {
        timestampMillis = TimeUnit.MICROSECONDS.toMillis(timestamp);
        index = indexNameFormatter.indexNameForTimestamp(timestampMillis);
      } else {
        timestampMillis = null;
        // guessTimestamp is made for determining the span's authoritative timestamp. When choosing
        // the index bucket, any annotation is better than using current time.
        Long indexTimestamp = null;
        for (int i = 0, length = span.annotations.size(); i < length; i++) {
          indexTimestamp = span.annotations.get(i).timestamp / 1000;
          break;
        }
        if (indexTimestamp == null) indexTimestamp = System.currentTimeMillis();
        index = indexNameFormatter.indexNameForTimestamp(indexTimestamp);
      }
      if (!span.name.isEmpty()) putServiceSpans(indexToServiceSpans, index, span);
      byte[] document = Codec.JSON.writeSpan(span);
      if (timestampMillis != null) document = prefixWithTimestampMillis(document, timestampMillis);
      indexer.add(index, ElasticsearchHttpSpanStore.SPAN, document, null /* Allow ES to choose an ID */);
    }
    return indexToServiceSpans;
  }

  void putServiceSpans(Map<String, Set<Pair<String>>> indexToServiceSpans, String index, Span s) {
    Set<Pair<String>> serviceSpans = indexToServiceSpans.get(index);
    if (serviceSpans == null) indexToServiceSpans.put(index, serviceSpans = new LinkedHashSet<>());
    for (String serviceName : s.serviceNames()) {
      serviceSpans.add(Pair.create(serviceName, s.name));
    }
  }

  /**
   * Adds service and span names to the pending batch. The id is "serviceName|spanName" to prevent
   * a large order of duplicates ending up in the daily index. This also means queries do not need
   * to deduplicate.
   */
  void indexNames(HttpBulkIndexer indexer, Map<String, Set<Pair<String>>> indexToServiceSpans)
      throws IOException {
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
        indexer.add(index, SERVICE_SPAN, document, serviceSpan._1 + "|" + serviceSpan._2);
      }
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
