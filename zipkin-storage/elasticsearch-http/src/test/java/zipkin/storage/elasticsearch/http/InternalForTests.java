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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.internal.CallbackCaptor;
import zipkin.internal.Pair;
import zipkin.storage.AsyncSpanConsumer;

import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanStore.DEPENDENCY_LINK;

/** Package accessor for integration tests */
public class InternalForTests {
  public static void writeDependencyLinks(ElasticsearchHttpStorage es, List<DependencyLink> links,
      long midnightUTC) {
    String index = es.indexNameFormatter().indexNameForTimestamp(midnightUTC);
    HttpBulkIndexer indexer = new HttpBulkIndexer("index-links", es);
    for (DependencyLink link : links) {
      byte[] document = Codec.JSON.writeDependencyLink(link);
      indexer.add(index, DEPENDENCY_LINK, document,
          link.parent + "|" + link.child); // Unique constraint
    }
    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    indexer.execute(callback);
    callback.get();
  }

  public static void clear(ElasticsearchHttpStorage es) throws IOException {
    es.clear();
    // clear servicespan cache in order to prevent tests breaking
    ((ElasticsearchHttpSpanConsumer)es.asyncSpanConsumer()).resetIndexToServiceSpansCache();
  }

  public static void flushOnWrites(ElasticsearchHttpStorage.Builder builder) {
    builder.flushOnWrites(true);
  }

  /** The old consumer didn't write to the "servicespan" type on ingest. */
  public static AsyncSpanConsumer oldConsumer(ElasticsearchHttpStorage es) {
    es.ensureIndexTemplate();
    return new ElasticsearchHttpSpanConsumer(es) {
      @Override void indexNames(HttpBulkIndexer ignored, Map<String, Set<Pair<String>>> ignored2) {
      }
    };
  }
}
