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
package zipkin.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.JsonCodec;
import zipkin.spanstore.guava.GuavaSpanConsumer;

// Extracted for readability
final class ElasticsearchSpanConsumer implements GuavaSpanConsumer {
  /**
   * Internal flag that allows you read-your-writes consistency during tests. With Elasticsearch,
   * it is not sufficient to block on the {@link #accept(List)} future since the index also needs
   * to be flushed.
   */
  @VisibleForTesting
  static boolean FLUSH_ON_WRITES;

  static final JsonCodec JSON_CODEC = new JsonCodec();

  private final Client client;
  private final IndexNameFormatter indexNameFormatter;

  ElasticsearchSpanConsumer(Client client, IndexNameFormatter indexNameFormatter) {
    this.client = client;
    this.indexNameFormatter = indexNameFormatter;
  }

  @Override
  public ListenableFuture<Void> accept(List<Span> spans) {
    BulkRequestBuilder request = client.prepareBulk();
    for (Span span : spans) {
      request.add(createSpanIndexRequest(ApplyTimestampAndDuration.apply(span)));
    }
    ListenableFuture<Void> future = toVoidFuture(request.execute());
    if (FLUSH_ON_WRITES) {
      future = Futures.transformAsync(
          future,
          new AsyncFunction<Void, Void>() {
            @Override public ListenableFuture<Void> apply(Void input) throws Exception {
              return toVoidFuture(client.admin().indices()
                  .prepareFlush(indexNameFormatter.catchAll())
                  .execute());
            }
          });
    }
    return future;
  }

  private static <T> ListenableFuture<Void> toVoidFuture(ListenableActionFuture<T> elasticFuture) {
    return Futures.transform(
        ElasticListenableFuture.of(elasticFuture),
        new Function<T, Void>() {
          @Override public Void apply(T input) {
            return null;
          }
        });
  }

  private IndexRequestBuilder createSpanIndexRequest(Span span) {
    long indexTimestampMillis;
    if (span.timestamp != null) {
      indexTimestampMillis = TimeUnit.MICROSECONDS.toMillis(span.timestamp);
    } else {
      indexTimestampMillis = System.currentTimeMillis();
    }
    String spanIndex = indexNameFormatter.indexNameForTimestamp(indexTimestampMillis);
    return client.prepareIndex(spanIndex, ElasticsearchConstants.SPAN)
        .setSource(Codec.JSON.writeSpan(span));
  }
}
