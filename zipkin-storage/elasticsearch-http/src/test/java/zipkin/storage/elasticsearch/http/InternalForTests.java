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
import java.io.UncheckedIOException;
import java.util.List;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.HttpBulkIndexer;

/** Package accessor for integration tests */
public class InternalForTests {
  public static void writeDependencyLinks(ElasticsearchStorage es, List<DependencyLink> links,
    long midnightUTC) {
    String index =
      es.indexNameFormatter().formatTypeAndTimestamp("dependency", midnightUTC);
    HttpBulkIndexer indexer = new HttpBulkIndexer("index-links", es);
    for (DependencyLink link : links) {
      byte[] document = Codec.JSON.writeDependencyLink(link);
      indexer.add(index, "dependency", document,
        link.parent + "|" + link.child); // Unique constraint
    }
    try {
      indexer.newCall().execute();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void clear(ElasticsearchHttpStorage es) throws IOException {
    es.clear();
  }

  public static void flushOnWrites(ElasticsearchHttpStorage.Builder builder) {
    builder.flushOnWrites(true);
  }
}
