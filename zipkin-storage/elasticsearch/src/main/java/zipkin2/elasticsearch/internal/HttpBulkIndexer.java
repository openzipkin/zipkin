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
package zipkin2.elasticsearch.internal;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSource;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.internal.Nullable;

import static zipkin2.internal.JsonEscaper.jsonEscape;

// See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
// exposed to re-use for testing writes of dependency links
public final class HttpBulkIndexer {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  final String tag;
  final HttpCall.Factory http;
  final String pipeline;
  final boolean flushOnWrites;

  // Mutated for each call to add
  final Buffer body = new Buffer();
  final Set<String> indices;
  final HttpCall.BodyConverter<Void> maybeFlush;

  public HttpBulkIndexer(String tag, ElasticsearchStorage es) {
    this.tag = tag;
    http = es.http();
    pipeline = es.pipeline();
    flushOnWrites = es.flushOnWrites();
    if (flushOnWrites) {
      indices = new LinkedHashSet<>();
      maybeFlush = new HttpCall.BodyConverter<Void>() {
        @Override public Void convert(BufferedSource b) throws IOException {
          CheckForErrors.INSTANCE.convert(b);
          if (indices.isEmpty()) return null;
          ElasticsearchStorage.flush(http, join(indices));
          return null;
        }
      };
    } else {
      indices = null;
      maybeFlush = CheckForErrors.INSTANCE;
    }
  }

  enum CheckForErrors implements HttpCall.BodyConverter<Void> {
    INSTANCE;

    @Override public Void convert(BufferedSource b) throws IOException {
      String content = b.readUtf8();
      if (content.contains("\"errors\":true")) throw new IllegalStateException(content);
      return null;
    }

    @Override public String toString() {
      return "CheckForErrors";
    }
  }

  public void add(String index, String typeName, byte[] document, @Nullable String id) {
    writeIndexMetadata(index, typeName, id);
    writeDocument(document);
  }

  void writeIndexMetadata(String index, String typeName, @Nullable String id) {
    if (flushOnWrites) indices.add(index);
    body.writeUtf8("{\"index\":{\"_index\":\"").writeUtf8(index).writeByte('"');
    // the _type parameter is needed for Elasticsearch <6.x
    body.writeUtf8(",\"_type\":\"").writeUtf8(typeName).writeByte('"');
    if (id != null) {
      body.writeUtf8(",\"_id\":\"").writeUtf8(jsonEscape(id)).writeByte('"');
    }
    body.writeUtf8("}}\n");
  }

  void writeDocument(byte[] document) {
    body.write(document);
    body.writeByte('\n');
  }

  /** Creates a bulk request when there is more than one object to store */
  public HttpCall<Void> newCall() {
    HttpUrl url = pipeline != null
      ? http.baseUrl.newBuilder("_bulk").addQueryParameter("pipeline", pipeline).build()
      : http.baseUrl.resolve("_bulk");

    Request request = new Request.Builder().url(url)
      .tag(tag)
      .post(RequestBody.create(APPLICATION_JSON, body.readByteString()))
      .build();

    return http.newCall(request, maybeFlush);
  }

  static String join(Collection<String> parts) {
    Iterator<String> iterator = parts.iterator();
    StringBuilder result = new StringBuilder(iterator.next());
    while (iterator.hasNext()) {
      result.append(',').append(iterator.next());
    }
    return result.toString();
  }
}
