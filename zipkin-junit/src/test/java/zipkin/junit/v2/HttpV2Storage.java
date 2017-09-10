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
package zipkin.junit.v2;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import zipkin2.CheckResult;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

/**
 * Test storage component that forwards requests to an HTTP endpoint.
 *
 * <p>Note: this inherits the {@link Builder#strictTraceId(boolean)} from the backend.
 */
final class HttpV2Storage extends StorageComponent {
  private final OkHttpClient client;
  private final HttpUrl baseUrl;
  private final HttpV2SpanStore spanStore;
  private final HttpV2SpanConsumer spanConsumer;

  /**
   * @param baseUrl Ex "http://localhost:9411"
   */
  HttpV2Storage(String baseUrl) {
    this.client = new OkHttpClient();
    this.baseUrl = HttpUrl.parse(baseUrl);
    this.spanStore = new HttpV2SpanStore(this.client, this.baseUrl);
    this.spanConsumer = new HttpV2SpanConsumer(this.client, this.baseUrl);
  }

  @Override public SpanStore spanStore() {
    return spanStore;
  }

  @Override public SpanConsumer spanConsumer() {
    return spanConsumer;
  }

  @Override public CheckResult check() {
    try {
      spanStore.getServiceNames();
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() {
    client.connectionPool().evictAll();
  }
}
