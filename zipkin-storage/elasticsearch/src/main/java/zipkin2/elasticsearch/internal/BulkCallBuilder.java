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
package zipkin2.elasticsearch.internal;

import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;

// See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
// exposed to re-use for testing writes of dependency links
public final class BulkCallBuilder {
  public static final int INDEX_CHARS_LIMIT = 256;
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  final String tag;
  final boolean shouldAddType;
  final HttpCall.Factory http;
  final String pipeline;
  final boolean waitForRefresh;

  // Mutated for each call to index
  final Buffer buffer = new Buffer();

  public BulkCallBuilder(ElasticsearchStorage es, float esVersion, String tag) {
    this.tag = tag;
    shouldAddType = esVersion < 7.0f;
    http = es.http();
    pipeline = es.pipeline();
    waitForRefresh = es.flushOnWrites();
  }

  enum CheckForErrors implements HttpCall.BodyConverter<Void> {
    INSTANCE;

    @Override public Void convert(BufferedSource b) throws IOException {
      String content = b.readUtf8();
      if (content.contains("\"status\":429")) throw new RejectedExecutionException(content);
      if (content.contains("\"errors\":true")) throw new IllegalStateException(content);
      return null;
    }

    @Override public String toString() {
      return "CheckForErrors";
    }
  }

  public <T> void index(String index, String typeName, T input, BulkIndexWriter<T> writer) {
    Buffer document = new Buffer();
    String id = writer.writeDocument(input, document);
    writeIndexMetadata(buffer, index, typeName, id);
    buffer.writeByte('\n');
    buffer.write(document, document.size());
    buffer.writeByte('\n');
  }

  void writeIndexMetadata(Buffer indexBuffer, String index, String typeName, String id) {
    JsonWriter jsonWriter = JsonWriter.of(indexBuffer);
    try {
      jsonWriter.beginObject();
      jsonWriter.name("index");
      jsonWriter.beginObject();
      jsonWriter.name("_index").value(index);
      // the _type parameter is needed for Elasticsearch < 6.x
      if (shouldAddType) jsonWriter.name("_type").value(typeName);
      jsonWriter.name("_id").value(id);
      jsonWriter.endObject();
      jsonWriter.endObject();
    } catch (IOException e) {
      throw new AssertionError(e); // No I/O writing to a Buffer.
    }
  }

  /** Creates a bulk request when there is more than one object to store */
  public HttpCall<Void> build() {
    HttpUrl.Builder urlBuilder = http.baseUrl.newBuilder("_bulk");
    if (pipeline != null) urlBuilder.addQueryParameter("pipeline", pipeline);
    if (waitForRefresh) urlBuilder.addQueryParameter("refresh", "wait_for");

    RequestBody body = new BufferRequestBody(buffer);

    Request request = new Request.Builder().url(urlBuilder.build()).tag(tag).post(body).build();
    return http.newCall(request, CheckForErrors.INSTANCE);
  }

  /** This avoids allocating a large byte array (by using a poolable buffer instead). */
  static final class BufferRequestBody extends RequestBody {
    final long contentLength;
    final Buffer buffer;

    BufferRequestBody(Buffer buffer) {
      this.contentLength = buffer.size();
      this.buffer = buffer;
    }

    @Override public MediaType contentType() {
      return APPLICATION_JSON;
    }

    @Override public long contentLength() {
      return contentLength;
    }

    @Override public boolean isOneShot() {
      return true;
    }

    @Override public void writeTo(BufferedSink sink) throws IOException {
      sink.write(buffer, contentLength);
    }
  }
}
