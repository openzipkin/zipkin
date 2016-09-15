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
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageAdapters;
import zipkin.storage.StorageComponent;

public final class ElasticsearchRestStorage implements StorageComponent {

  /**
   * Internal flag that allows you read-your-writes consistency during tests. With Elasticsearch, it
   * is not sufficient to block on futures since the index also needs to be flushed.
   */
  @VisibleForTesting
  static boolean FLUSH_ON_WRITES;

  final ElasticsearchStorage delegate;
  final OkHttpClient client;
  final HttpUrl baseUrl;
  final Moshi moshi;

  public ElasticsearchRestStorage(ElasticsearchStorage delegate) {
    this.delegate = delegate;
    this.client = new OkHttpClient();
    this.baseUrl = HttpUrl.parse("http://localhost:9200");
    this.moshi = new Moshi.Builder()
        .add(Span.class, new InternalSpanAdapter())
        .add(DependencyLink.class, new InternalDependencyLinkAdapter()).build();
  }

  @Override public SpanStore spanStore() {
    return StorageAdapters.asyncToBlocking(asyncSpanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return new ElasticsearchRestSpanStore(delegate.asyncSpanStore(), client, baseUrl, moshi,
        delegate.indexNameFormatter);
  }

  @Override public AsyncSpanConsumer asyncSpanConsumer() {
    return new ElasticsearchRestSpanConsumer(client, baseUrl, moshi, delegate.indexNameFormatter);
  }

  @Override public CheckResult check() {
    return delegate.check();
  }

  @Override public void close() throws IOException {
    Dispatcher dispatcher = client.dispatcher();
    dispatcher.executorService().shutdown();
    try {
      if (!dispatcher.executorService().awaitTermination(1, TimeUnit.SECONDS)) {
        dispatcher.cancelAll();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    delegate.close();
  }

  void clear() {
    delegate.clear();
  }
}
