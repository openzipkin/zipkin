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

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static zipkin.storage.StorageAdapters.blockingToAsync;

/**
 * Test storage component that forwards requests to an HTTP endpoint.
 *
 * <p>Note: this inherits the {@link StorageComponent.Builder#strictTraceId(boolean)} from the
 * backend.
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
    // TODO: rewrite http span store to default to async
    this.asyncSpanStore = blockingToAsync(spanStore, Runnable::run);
    this.consumer = new HttpSpanConsumer(this.client, this.baseUrl);
  }

  @Override public SpanStore spanStore() {
    return spanStore;
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return asyncSpanStore;
  }

  @Override
  public AsyncSpanConsumer asyncSpanConsumer() {
    return consumer;
  }

  @Override public CheckResult check() {
    try {
      spanStore.getServiceNames();
    } catch (RuntimeException e){
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() {
    client.connectionPool().evictAll();
  }
}
