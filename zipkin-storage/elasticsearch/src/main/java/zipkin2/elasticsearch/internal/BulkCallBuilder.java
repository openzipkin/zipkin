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
package zipkin2.elasticsearch.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.auto.value.AutoValue;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.QueryStringEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.HttpCall.InputStreamConverter;

import static zipkin2.elasticsearch.internal.JsonSerializers.OBJECT_MAPPER;

// See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
// exposed to re-use for testing writes of dependency links
public final class BulkCallBuilder {
  static final InputStreamConverter<Void> CHECK_FOR_ERRORS = new InputStreamConverter<Void>() {
    @Override public Void convert(InputStream content) {
      RuntimeException toThrow = null;
      try {
        JsonNode tree = OBJECT_MAPPER.readTree(content);
        Number status = tree.findPath("status").numberValue();
        if (status != null && status.intValue() == 429) {
          toThrow = new RejectedExecutionException(tree.toString());
        } else if (tree.path("/errors").booleanValue()) {
          toThrow = new RuntimeException(content.toString());
        }
      } catch (RuntimeException | IOException possiblyParseException) {
      }
      if (toThrow != null) throw toThrow;
      return null;
    }

    @Override public String toString() {
      return "CheckForErrors";
    }
  };

  final String tag;
  final boolean shouldAddType;
  final HttpCall.Factory http;
  final String pipeline;
  final boolean waitForRefresh;

  // Mutated for each call to index
  final List<IndexEntry<?>> entries = new ArrayList<>();

  public BulkCallBuilder(ElasticsearchStorage es, float esVersion, String tag) {
    this.tag = tag;
    shouldAddType = esVersion < 7.0f;
    http = es.http();
    pipeline = es.pipeline();
    waitForRefresh = es.flushOnWrites();
  }

  static <T> IndexEntry<T> newIndexEntry(String index, String typeName, T input,
    BulkIndexWriter<T> writer) {
    return new AutoValue_BulkCallBuilder_IndexEntry<>(index, typeName, input, writer);
  }

  @AutoValue static abstract class IndexEntry<T> {
    abstract String index();

    abstract String typeName();

    abstract T input();

    abstract BulkIndexWriter<T> writer();
  }

  public <T> void index(String index, String typeName, T input, BulkIndexWriter<T> writer) {
    entries.add(newIndexEntry(index, typeName, input, writer));
  }

  /** Creates a bulk request when there is more than one object to store */
  public HttpCall<Void> build() {
    QueryStringEncoder urlBuilder = new QueryStringEncoder("/_bulk");
    if (pipeline != null) urlBuilder.addParam("pipeline", pipeline);
    if (waitForRefresh) urlBuilder.addParam("refresh", "wait_for");

    final HttpData body;

    // While ideally we can use a direct buffer for our request, we can't do so and support the
    // semantics of our Call so instead construct a normal byte[] with all the serialized documents.
    // Using a composite buffer means we essentially create a list of documents each serialized into
    // pooled heap buffers, and then only copy once when consolidating them into an unpooled byte[],
    // while if we used a normal buffer, we would have more copying in intermediate steps if we need
    // to resize the buffer (we can only do a best effort estimate of the buffer size and will often
    // still need to resize).
    CompositeByteBuf sink = RequestContext.mapCurrent(
      RequestContext::alloc, () -> PooledByteBufAllocator.DEFAULT)
      .compositeHeapBuffer(Integer.MAX_VALUE);
    try {
      for (IndexEntry<?> entry : entries) {
        write(sink, entry, shouldAddType);
      }
      body = HttpData.wrap(ByteBufUtil.getBytes(sink));
    } finally {
      sink.release();
    }

    AggregatedHttpRequest request = AggregatedHttpRequest.of(
      RequestHeaders.of(
        HttpMethod.POST, urlBuilder.toString(),
        HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8),
      body);
    return http.newCall(request, CHECK_FOR_ERRORS, tag);
  }

  static <T> void write(CompositeByteBuf sink, IndexEntry<T> entry,
    boolean shouldAddType) {
    // Fuzzily assume a general small span is 600 bytes to reduce resizing while building up the
    // JSON. Any extra bytes will be released back after serializing all the documents.
    ByteBuf document = sink.alloc().heapBuffer(600).writeByte('\n');
    ByteBuf metadata = sink.alloc().heapBuffer(200);
    try {
      String id = entry.writer().writeDocument(entry.input(), new ByteBufOutputStream(document));
      document.writeByte('\n');
      writeIndexMetadata(new ByteBufOutputStream(metadata), entry, id, shouldAddType);
    } catch (Throwable t) {
      document.release();
      metadata.release();
      throw t;
    }
    sink.addComponent(true, metadata).addComponent(true, document);
  }

  static <T> void writeIndexMetadata(ByteBufOutputStream sink, IndexEntry<T> entry, String id,
    boolean shouldAddType) {
    try (JsonGenerator writer = JsonSerializers.jsonGenerator(sink)) {
      writer.writeStartObject();
      writer.writeObjectFieldStart("index");
      writer.writeStringField("_index", entry.index());
      // the _type parameter is needed for Elasticsearch < 6.x
      if (shouldAddType) writer.writeStringField("_type", entry.typeName());
      writer.writeStringField("_id", id);
      writer.writeEndObject();
      writer.writeEndObject();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
  }
}
