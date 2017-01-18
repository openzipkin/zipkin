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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import zipkin.internal.Nullable;
import zipkin.storage.Callback;

// See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
// exposed to re-use for testing writes of dependency links
abstract class HttpBulkIndexer<T> {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  // Immutable fields
  final HttpClient client;
  final String typeName;
  final String tag;

  // Mutated for each call to add
  final Buffer body = new Buffer();
  final Set<String> indices = new LinkedHashSet<>();

  HttpBulkIndexer(HttpClient client, String typeName) {
    this.client = client;
    this.typeName = typeName;
    this.tag = "index-" + typeName;
  }

  void add(String index, T object, @Nullable String id) {
    writeIndexMetadata(index, id);
    writeDocument(object);

    if (client.flushOnWrites) indices.add(index);
  }

  void writeIndexMetadata(String index, @Nullable String id) {
    body.writeUtf8("{\"index\":{\"_index\":\"").writeUtf8(index).writeByte('"');
    body.writeUtf8(",\"_type\":\"").writeUtf8(typeName).writeByte('"');
    if (id != null) {
      body.writeUtf8(",\"_id\":\"").writeUtf8(id).writeByte('"');
    }
    body.writeUtf8("}}\n");
  }

  void writeDocument(T object) {
    body.write(toJsonBytes(object));
    body.writeByte('\n');
  }

  abstract byte[] toJsonBytes(T object);

  /** Creates a bulk request when there is more than one object to store */
  public void execute(Callback<Void> callback) { // public to allow interface retrofit
    HttpUrl url = client.pipeline != null
        ? client.baseUrl.newBuilder("_bulk").addQueryParameter("pipeline", client.pipeline).build()
        : client.baseUrl.resolve("_bulk");

    Request request = new Request.Builder().url(url).tag(tag)
        .post(RequestBody.create(APPLICATION_JSON, body.readByteString())).build();

    new CallbackAdapter<Void>(client.http.newCall(request), callback) {
      @Override Void convert(ResponseBody responseBody) throws IOException {
        if (indices.isEmpty()) return null;
        Iterator<String> index = indices.iterator();
        StringBuilder indexString = new StringBuilder(index.next());
        while (index.hasNext()) {
          indexString.append(',').append(index.next());
        }
        client.flush(indexString.toString());
        return null;
      }
    }.enqueue();
  }
}
