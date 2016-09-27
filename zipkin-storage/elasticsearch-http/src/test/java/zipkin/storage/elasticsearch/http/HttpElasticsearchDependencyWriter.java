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

import com.google.common.collect.ImmutableMap;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import io.searchbox.indices.Flush;
import java.io.IOException;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static com.google.common.base.Preconditions.checkArgument;

public class HttpElasticsearchDependencyWriter {
  public static void writeDependencyLinks(InternalElasticsearchClient genericClient,
      List<DependencyLink> links, String index, String type) throws IOException {
    checkArgument(genericClient instanceof HttpClient, "");
    HttpClient client = (HttpClient) genericClient;
    Bulk.Builder batch = new Bulk.Builder();
    for (DependencyLink link : links) {
      batch.addAction(new Index.Builder(ImmutableMap.of(
          "parent", link.parent,
          "child", link.child,
          "callCount", link.callCount
      )).id(link.parent + "|" + link.child) // Unique constraint
          .index(index)
          .type(type)
          .build());
    }

    client.client.execute(batch.build());
    client.client.execute(new Flush.Builder().addIndex(index).build());
  }
}
