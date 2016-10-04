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

import java.util.List;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static com.google.common.base.Preconditions.checkArgument;

public class HttpElasticsearchDependencyWriter {
  public static void writeDependencyLinks(InternalElasticsearchClient genericClient,
      List<DependencyLink> links, String index, String type) throws Exception {
    checkArgument(genericClient instanceof HttpClient, "");
    HttpClient client = (HttpClient) genericClient;
    HttpBulkIndexer<DependencyLink> indexer = new HttpBulkIndexer<DependencyLink>(client, type){
      @Override byte[] toJsonBytes(DependencyLink link) {
        return Codec.JSON.writeDependencyLink(link);
      }
    };
    for (DependencyLink link : links) {
      indexer.add(index, link, link.parent + "|" + link.child); // Unique constraint
    }
    indexer.execute().get();
  }
}
