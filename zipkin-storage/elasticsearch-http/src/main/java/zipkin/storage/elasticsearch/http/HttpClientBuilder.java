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

import java.util.List;
import okhttp3.OkHttpClient;
import zipkin.internal.Lazy;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static zipkin.internal.Util.checkNotNull;

/**
 * @deprecated Please use {@link ElasticsearchHttpStorage} instead
 */
@Deprecated
public final class HttpClientBuilder extends InternalElasticsearchClient.Builder {
  final ElasticsearchHttpStorage.Builder builder;

  public static HttpClientBuilder create(OkHttpClient client) {
    return new HttpClientBuilder(ElasticsearchHttpStorage.builder(client));
  }

  HttpClientBuilder(ElasticsearchHttpStorage.Builder builder) {
    this.builder = builder;
  }

  /**
   * Unnecessary, but we'll check it's not null for consistency's sake.
   */
  @Override public HttpClientBuilder cluster(String cluster) {
    checkNotNull(cluster, "cluster");
    return this;
  }

  /**
   * A list of elasticsearch nodes to connect to, in http://host:port or https://host:port
   * format. Defaults to "http://localhost:9200".
   */
  @Override public HttpClientBuilder hosts(Lazy<List<String>> hosts) {
    this.builder.hostsSupplier(() -> hosts.get());
    return this;
  }

  /** Sets maximum in-flight requests from this process to any Elasticsearch host. Defaults to 64 */
  public HttpClientBuilder maxRequests(int maxRequests) {
    this.builder.maxRequests(maxRequests);
    return this;
  }

  /**
   * Only valid when the destination is Elasticsearch 5.x. Indicates the ingest pipeline used before
   * spans are indexed. No default.
   *
   * <p>See https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html
   */
  public HttpClientBuilder pipeline(String pipeline) {
    this.builder.pipeline(pipeline);
    return this;
  }

  /** Visible for testing */
  @Override public HttpClientBuilder flushOnWrites(boolean flushOnWrites) {
    this.builder.flushOnWrites(flushOnWrites);
    return this;
  }

  @Override public InternalElasticsearchClient.Factory buildFactory() {
    return new HttpClient.Factory(this);
  }
}
