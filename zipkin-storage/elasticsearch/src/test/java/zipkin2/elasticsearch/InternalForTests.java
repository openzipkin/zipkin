/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonGenerator;
import io.netty.buffer.ByteBufOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.elasticsearch.internal.BulkCallBuilder;
import zipkin2.elasticsearch.internal.BulkIndexWriter;
import zipkin2.elasticsearch.internal.JsonSerializers;

/** Package accessor for integration tests */
public class InternalForTests {
  public static void writeDependencyLinks(ElasticsearchStorage es, List<DependencyLink> links,
    long midnightUTC) {
    String index = ((ElasticsearchSpanConsumer) es.spanConsumer())
      .formatTypeAndTimestampForInsert("dependency", midnightUTC);
    BulkCallBuilder indexer = new BulkCallBuilder(es, es.version(), "indexlinks");
    for (DependencyLink link : links)
      indexer.index(index, "dependency", link, DEPENDENCY_LINK_WRITER);
    try {
      indexer.build().execute();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static final BulkIndexWriter<DependencyLink> DEPENDENCY_LINK_WRITER =
    new BulkIndexWriter<DependencyLink>() {
      @Override public String writeDocument(DependencyLink link, ByteBufOutputStream sink) {
        try (JsonGenerator writer = JsonSerializers.jsonGenerator(sink)) {
          writer.writeStartObject();
          writer.writeStringField("parent", link.parent());
          writer.writeStringField("child", link.child());
          writer.writeNumberField("callCount", link.callCount());
          if (link.errorCount() > 0) writer.writeNumberField("errorCount", link.errorCount());
          writer.writeEndObject();
        } catch (IOException e) {
          throw new AssertionError(e); // No I/O writing to a Buffer.
        }
        return link.parent() + "|" + link.child();
      }
    };
}
