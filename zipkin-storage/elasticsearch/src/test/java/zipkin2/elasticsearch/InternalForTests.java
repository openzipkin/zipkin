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

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.elasticsearch.internal.BulkIndexSupport;
import zipkin2.elasticsearch.internal.HttpBulkIndexer;

/** Package accessor for integration tests */
public class InternalForTests {
  public static void writeDependencyLinks(ElasticsearchStorage es, List<DependencyLink> links,
    long midnightUTC) {
    String index = ((ElasticsearchSpanConsumer) es.spanConsumer())
      .formatTypeAndTimestampForInsert("dependency", midnightUTC);
    HttpBulkIndexer indexer = new HttpBulkIndexer("indexlinks", es);
    for (DependencyLink link : links) {
      indexer.add(index, "dependency", link, DEPENDENCY_LINK_BULK_INDEX_SUPPORT);
    }
    try {
      indexer.newCall().execute();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static final BulkIndexSupport<DependencyLink> DEPENDENCY_LINK_BULK_INDEX_SUPPORT =
    new BulkIndexSupport<DependencyLink>() {
      @Override public String writeDocument(DependencyLink link, JsonWriter writer) {
        try {
          writer.beginObject();
          writer.name("parent").value(link.parent());
          writer.name("child").value(link.child());
          writer.name("callCount").value(link.callCount());
          if (link.errorCount() > 0) writer.name("errorCount").value(link.errorCount());
          writer.endObject();
        } catch (IOException e) {
          throw new AssertionError(e); // No I/O writing to a Buffer.
        }
        return link.parent() + "|" + link.child();
      }
    };
}
