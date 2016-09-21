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

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import zipkin.internal.LazyCloseable;

final class LazyClient extends LazyCloseable<InternalElasticsearchClient> {
  private final InternalElasticsearchClient.Factory clientFactory;
  private final String indexTemplateName;
  final String indexTemplate;
  private final String allIndices;

  LazyClient(ElasticsearchStorage.Builder builder) {
    this.clientFactory = builder.clientBuilder.buildFactory();
    this.indexTemplateName = builder.index + "_template"; // should be 1:1 with indices
    this.allIndices = new IndexNameFormatter(builder.index).catchAll();
    try {
      this.indexTemplate = Resources.toString(
          Resources.getResource("zipkin/storage/elasticsearch/zipkin_template.json"),
          StandardCharsets.UTF_8)
          .replace("${__INDEX__}", builder.index)
          .replace("${__NUMBER_OF_SHARDS__}", String.valueOf(builder.indexShards))
          .replace("${__NUMBER_OF_REPLICAS__}", String.valueOf(builder.indexReplicas));
    } catch (IOException e) {
      throw new AssertionError("Error reading jar resource, shouldn't happen.", e);
    }
  }

  @Override protected InternalElasticsearchClient compute() {
    InternalElasticsearchClient client = clientFactory.create(allIndices);
    client.ensureTemplate(indexTemplateName, indexTemplate);
    return client;
  }

  @Override public String toString() {
    return clientFactory.toString();
  }

  @Override
  public void close() {
    InternalElasticsearchClient maybeNull = maybeNull();
    if (maybeNull != null) maybeNull.close();
  }
}
