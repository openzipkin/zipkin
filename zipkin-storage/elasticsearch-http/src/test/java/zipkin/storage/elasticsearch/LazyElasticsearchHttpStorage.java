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

import com.google.common.collect.ImmutableList;
import okhttp3.OkHttpClient;
import zipkin.storage.elasticsearch.http.HttpClientBuilder;

public class LazyElasticsearchHttpStorage extends LazyElasticsearchStorage {

  public LazyElasticsearchHttpStorage(String image) {
    super(image);
  }

  @Override public ElasticsearchStorage.Builder computeStorageBuilder() {
    return ElasticsearchStorage.builder(
        HttpClientBuilder.create(new OkHttpClient())
            .flushOnWrites(true)
            .hosts(ImmutableList.of("http://" + getEndpoint(9200))))
        .index("test_zipkin_http");
  }
}
