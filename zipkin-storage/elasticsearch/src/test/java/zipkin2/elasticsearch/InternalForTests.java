/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
