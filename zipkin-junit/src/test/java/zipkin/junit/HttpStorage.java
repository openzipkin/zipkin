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
package zipkin.junit;

import java.util.concurrent.Executor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.CollectorSampler;
import zipkin.SpanStore;
import zipkin.StorageComponent;

import static zipkin.StorageAdapters.blockingToAsync;
import static zipkin.StorageAdapters.makeSampled;
import static zipkin.internal.Util.checkNotNull;

/**
 * Test storage component that keeps all spans in memory, accepting them on the calling thread.
 */
final class HttpStorage implements StorageComponent {
  private final OkHttpClient client;
  private final HttpUrl baseUrl;
  private final SpanStore spanStore;
  private final AsyncSpanStore asyncSpanStore;
  private final AsyncSpanConsumer consumer;

  /**
   * @param baseUrl Ex "http://localhost:9411"
   */
  HttpStorage(String baseUrl) {
    this.client = new OkHttpClient();
    this.baseUrl = HttpUrl.parse(baseUrl);
    this.spanStore = new HttpSpanStore(this.client, this.baseUrl);
    this.asyncSpanStore = blockingToAsync(spanStore, new Executor() {
      @Override public void execute(Runnable command) {
        command.run();
      }
    }); // TODO: rewrite http span store to default to async
    this.consumer = new HttpSpanConsumer(this.client, this.baseUrl);
  }

  @Override public SpanStore spanStore() {
    return spanStore;
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return asyncSpanStore;
  }

  @Override public AsyncSpanConsumer asyncSpanConsumer(CollectorSampler sampler) {
    return makeSampled(consumer, checkNotNull(sampler, "sampler"));
  }

  @Override public void close() {
    client.connectionPool().evictAll();
  }
}
