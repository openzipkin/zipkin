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

import com.google.auto.value.AutoValue;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.squareup.moshi.JsonWriter;
import io.netty.handler.codec.http.QueryStringEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import okio.Buffer;
import okio.BufferedSink;
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

    Buffer okioBuffer = new Buffer();
    for (IndexEntry<?> entry : entries) {
      write(okioBuffer, entry, shouldAddType);
    }

    HttpData body = HttpData.wrap(okioBuffer.readByteArray());

    AggregatedHttpRequest request = AggregatedHttpRequest.of(
      RequestHeaders.of(
        HttpMethod.POST, urlBuilder.toString(),
        HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8),
      body);
    return http.newCall(request, CheckForErrors.INSTANCE);
  }

  static void write(BufferedSink sink, IndexEntry entry, boolean shouldAddType) {
    Buffer document = new Buffer();
    String id = entry.writer().writeDocument(entry.input(), document);
    writeIndexMetadata(sink, entry, id, shouldAddType);
    try {
      sink.writeByte('\n');
      sink.write(document, document.size());
      sink.writeByte('\n');
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
  }

  static void writeIndexMetadata(BufferedSink sink, IndexEntry entry, String id,
    boolean shouldAddType) {
    JsonWriter jsonWriter = JsonWriter.of(sink);
    try {
      jsonWriter.beginObject();
      jsonWriter.name("index");
      jsonWriter.beginObject();
      jsonWriter.name("_index").value(entry.index());
      // the _type parameter is needed for Elasticsearch < 6.x
      if (shouldAddType) jsonWriter.name("_type").value(entry.typeName());
      jsonWriter.name("_id").value(id);
      jsonWriter.endObject();
      jsonWriter.endObject();
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
