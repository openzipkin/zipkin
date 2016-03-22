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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import zipkin.Codec;
import zipkin.Span;
import zipkin.SpanConsumer;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.JsonCodec;

// Extracted for readability
final class ElasticsearchSpanConsumer implements SpanConsumer {

  static final JsonCodec JSON_CODEC = new JsonCodec();
  /**
   * Internal flag that allows you read-your-writes consistency during tests.
   *
   * <p>This is internal as collection endpoints are usually in different threads or not in the same
   * process as query ones. Special-casing this allows tests to pass without changing {@link
   * SpanConsumer#accept}.
   *
   * <p>Why not just change {@link SpanConsumer#accept} now? {@link SpanConsumer#accept} may indeed
   * need to change, but when that occurs, we'd want to choose something that is widely supportable,
   * and serving a specific use case. That api might not be a future, for example. Future is
   * difficult, for example, properly supporting and testing cancel. Further, there are other async
   * models such as callbacks that could be more supportable. Regardless, this work is best delayed
   * until there's a worthwhile use-case vs up-fronting only due to tests, and prematurely choosing
   * Future results.
   */
  static boolean BLOCK_ON_FUTURES;

  private final Client client;
  private final IndexNameFormatter indexNameFormatter;

  ElasticsearchSpanConsumer(Client client, IndexNameFormatter indexNameFormatter) {
    this.client = client;
    this.indexNameFormatter = indexNameFormatter;
  }

  @Override
  public void accept(List<Span> spans) {
    BulkRequestBuilder request = client.prepareBulk();
    for (Span span : spans) {
      request.add(createSpanIndexRequest(ApplyTimestampAndDuration.apply(span)));
    }
    ListenableActionFuture<BulkResponse> future = request.execute();
    if (BLOCK_ON_FUTURES) {
      future.actionGet();
      client.admin().indices().flush(new FlushRequest()).actionGet();
    }
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
