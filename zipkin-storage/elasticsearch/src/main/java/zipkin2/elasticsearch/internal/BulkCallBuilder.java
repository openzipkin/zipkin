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
import com.google.auto.value.AutoValue;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.QueryStringEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;

// See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
// exposed to re-use for testing writes of dependency links
public final class BulkCallBuilder {

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

    CompositeByteBuf sink = PooledByteBufAllocator.DEFAULT.compositeHeapBuffer();
    try {
      ByteBufOutputStream sinkStream = new ByteBufOutputStream(sink);
      for (IndexEntry<?> entry : entries) {
        write(sink, sinkStream, entry, shouldAddType);
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
    return http.newCall(request, CheckForErrors.INSTANCE);
  }

  static void write(CompositeByteBuf sink, ByteBufOutputStream sinkStream, IndexEntry entry,
    boolean shouldAddType) {
    // Fuzzily assume a general small span is 500 bytes to reduce resizing while building up the
    // JSON. Any extra bytes will be released back after serializing all the documents.
    ByteBuf document = sink.alloc().heapBuffer(500);
    String id = entry.writer().writeDocument(entry.input(), new ByteBufOutputStream(document));
    writeIndexMetadata(sinkStream, entry, id, shouldAddType);
    sink.writeByte('\n');
    sink.addComponent(true, document);
    sink.writeByte('\n');
  }

  static void writeIndexMetadata(ByteBufOutputStream sink, IndexEntry entry, String id,
    boolean shouldAddType) {
    JsonGenerator writer = JsonAdapters.jsonGenerator(sink);
    try {
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

  enum CheckForErrors implements HttpCall.BodyConverter<Void> {
    INSTANCE;

    @Override public Void convert(ByteBuffer b) throws IOException {
      String content = StandardCharsets.UTF_8.decode(b).toString();
      if (content.contains("\"status\":429")) throw new RejectedExecutionException(content);
      if (content.contains("\"errors\":true")) throw new IllegalStateException(content);
      return null;
    }

    @Override public String toString() {
      return "CheckForErrors";
    }
  }
}
