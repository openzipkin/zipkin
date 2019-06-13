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
package zipkin2.elasticsearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.BulkCallBuilder;
import zipkin2.elasticsearch.internal.BulkIndexWriter;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.internal.DelayLimiter;
import zipkin2.storage.SpanConsumer;

import static zipkin2.elasticsearch.ElasticsearchAutocompleteTags.AUTOCOMPLETE;
import static zipkin2.elasticsearch.ElasticsearchSpanStore.SPAN;
import static zipkin2.internal.Platform.SHORT_STRING_LENGTH;

class ElasticsearchSpanConsumer implements SpanConsumer { // not final for testing

  final ElasticsearchStorage es;
  final Set<String> autocompleteKeys;
  final IndexNameFormatter indexNameFormatter;
  final char indexTypeDelimiter;
  final boolean searchEnabled;
  final DelayLimiter<AutocompleteContext> delayLimiter;

  ElasticsearchSpanConsumer(ElasticsearchStorage es) {
    this.es = es;
    this.autocompleteKeys = new LinkedHashSet<>(es.autocompleteKeys());
    this.indexNameFormatter = es.indexNameFormatter();
    this.indexTypeDelimiter = es.indexTypeDelimiter();
    this.searchEnabled = es.searchEnabled();
    this.delayLimiter = DelayLimiter.newBuilder()
      .ttl(es.autocompleteTtl())
      .cardinality(es.autocompleteCardinality()).build();
  }

  String formatTypeAndTimestampForInsert(String type, long timestampMillis) {
    return indexNameFormatter.formatTypeAndTimestampForInsert(type, indexTypeDelimiter,
      timestampMillis);
  }

  @Override public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    BulkSpanIndexer indexer = new BulkSpanIndexer(this);
    indexSpans(indexer, spans);
    return indexer.newCall();
  }

  void indexSpans(BulkSpanIndexer indexer, List<Span> spans) {
    for (Span span : spans) {
      final long indexTimestamp; // which index to store this span into
      if (span.timestampAsLong() != 0L) {
        indexTimestamp = span.timestampAsLong() / 1000L;
      } else if (!span.annotations().isEmpty()) {
        // guessTimestamp is made for determining the span's authoritative timestamp. When choosing
        // the index bucket, any annotation is better than using current time.
        indexTimestamp = span.annotations().get(0).timestamp() / 1000L;
      } else {
        indexTimestamp = System.currentTimeMillis();
      }
      indexer.add(indexTimestamp, span);
      if (searchEnabled && !span.tags().isEmpty()) {
        indexer.addAutocompleteValues(indexTimestamp, span);
      }
    }
  }

  /** Mutable type used for each call to store spans */
  static final class BulkSpanIndexer {
    final BulkCallBuilder bulkCallBuilder;
    final ElasticsearchSpanConsumer consumer;
    final List<AutocompleteContext> pendingAutocompleteContexts = new ArrayList<>();
    final BulkIndexWriter<Span> spanWriter;

    BulkSpanIndexer(ElasticsearchSpanConsumer consumer) {
      this.bulkCallBuilder = new BulkCallBuilder(consumer.es, consumer.es.version(), "index-span");
      this.consumer = consumer;
      this.spanWriter =
        consumer.searchEnabled ? BulkIndexWriter.SPAN : BulkIndexWriter.SPAN_SEARCH_DISABLED;
    }

    void add(long indexTimestamp, Span span) {
      String index = consumer.formatTypeAndTimestampForInsert(SPAN, indexTimestamp);
      bulkCallBuilder.index(index, SPAN, span, spanWriter);
    }

    void addAutocompleteValues(long indexTimestamp, Span span) {
      String idx = consumer.formatTypeAndTimestampForInsert(AUTOCOMPLETE, indexTimestamp);
      for (Map.Entry<String, String> tag : span.tags().entrySet()) {
        int length = tag.getKey().length() + tag.getValue().length() + 1;
        if (length > SHORT_STRING_LENGTH) continue;

        // If the autocomplete whitelist doesn't contain the key, skip storing its value
        if (!consumer.autocompleteKeys.contains(tag.getKey())) continue;

        AutocompleteContext context =
          new AutocompleteContext(indexTimestamp, tag.getKey(), tag.getValue());
        if (!consumer.delayLimiter.shouldInvoke(context)) continue;
        pendingAutocompleteContexts.add(context);

        bulkCallBuilder.index(idx, AUTOCOMPLETE, tag, BulkIndexWriter.AUTOCOMPLETE);
      }
    }

    Call<Void> newCall() {
      Call<Void> storeCall = bulkCallBuilder.build();
      if (pendingAutocompleteContexts.isEmpty()) return storeCall;
      return storeCall.handleError((error, callback) -> {
        for (AutocompleteContext context : pendingAutocompleteContexts) {
          consumer.delayLimiter.invalidate(context);
        }
        callback.onError(error);
      });
    }
  }

  static final class AutocompleteContext {
    final long timestamp;
    final String key, value;

    AutocompleteContext(long timestamp, String key, String value) {
      this.timestamp = timestamp;
      this.key = key;
      this.value = value;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof AutocompleteContext)) return false;
      AutocompleteContext that = (AutocompleteContext) o;
      return timestamp == that.timestamp && key.equals(that.key) && value.equals(that.value);
    }

    @Override public int hashCode() {
      int h$ = 1;
      h$ *= 1000003;
      h$ ^= (int) (h$ ^ ((timestamp >>> 32) ^ timestamp));
      h$ *= 1000003;
      h$ ^= key.hashCode();
      h$ *= 1000003;
      h$ ^= value.hashCode();
      return h$;
    }
  }
}
