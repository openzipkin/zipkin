/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.elasticsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.elasticsearch.internal.HttpBulkIndexer;

/** Package accessor for integration tests */
public class InternalForTests {
  public static void writeDependencyLinks(ElasticsearchStorage es, List<DependencyLink> links,
    long midnightUTC) {
    String index =
      es.indexNameFormatter().formatTypeAndTimestamp("dependency", midnightUTC);
    HttpBulkIndexer indexer = new HttpBulkIndexer("indexlinks", es);
    for (DependencyLink link : links) {
      byte[] document = DependencyLinkBytesEncoder.JSON_V1.encode(link);
      indexer.add(index, "dependency", document,
        link.parent() + "|" + link.child()); // Unique constraint
    }
    try {
      indexer.newCall().execute();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
