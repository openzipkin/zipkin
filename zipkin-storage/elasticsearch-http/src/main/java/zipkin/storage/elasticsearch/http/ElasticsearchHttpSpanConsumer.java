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
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.propagateIfFatal;

final class ElasticsearchHttpSpanConsumer implements AsyncSpanConsumer {

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
      indexSpans(new HttpBulkSpanIndexer(es), spans).execute(callback);
    } catch (Throwable t) {
      propagateIfFatal(t);
      callback.onError(t);
    }
  }

  HttpBulkSpanIndexer indexSpans(HttpBulkSpanIndexer indexer, List<Span> spans) throws IOException {
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
}
