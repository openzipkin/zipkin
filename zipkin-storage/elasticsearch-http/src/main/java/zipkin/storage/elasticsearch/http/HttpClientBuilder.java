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
package zipkin.storage.elasticsearch.http;

import java.util.Collections;
import java.util.List;
import okhttp3.OkHttpClient;
import zipkin.internal.Lazy;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static com.google.common.base.Preconditions.checkNotNull;

public final class HttpClientBuilder extends InternalElasticsearchClient.Builder {
  final OkHttpClient client;
  Lazy<List<String>> hosts;
  boolean compressionEnabled = true;
  boolean flushOnWrites;

  public static HttpClientBuilder create(OkHttpClient client) {
    return new HttpClientBuilder(client);
  }

  HttpClientBuilder(OkHttpClient client) {
    this.client = checkNotNull(client, "client");
    hosts(Collections.singletonList("http://localhost:9200"));
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
    this.hosts = checkNotNull(hosts, "hosts");
    return this;
  }

  /** Default true. true implies that spans will be gzipped before transport. */
  public HttpClientBuilder compressionEnabled(boolean compressionEnabled) {
    this.compressionEnabled = compressionEnabled;
    return this;
  }

  /** Visible for testing */
  @Override public HttpClientBuilder flushOnWrites(boolean flushOnWrites) {
    this.flushOnWrites = flushOnWrites;
    return this;
  }

  @Override public InternalElasticsearchClient.Factory buildFactory() {
    return new HttpClient.Factory(this);
  }
}
