/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;

final class ElasticsearchRestSpanConsumer implements AsyncSpanConsumer {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  final OkHttpClient client;
  final HttpUrl baseUrl;
  final JsonAdapter<Span> spanAdapter;
  final JsonAdapter<IndexActionAndMetadata> indexActionAndMetadataAdapter;
  final IndexNameFormatter indexNameFormatter;
  final Request flushRequest;

  ElasticsearchRestSpanConsumer(OkHttpClient client, HttpUrl baseUrl,
      Moshi moshi, IndexNameFormatter indexNameFormatter) {
    this.client = client;
    this.baseUrl = baseUrl;
    this.spanAdapter = moshi.adapter(Span.class);
    this.indexActionAndMetadataAdapter = moshi.adapter(IndexActionAndMetadata.class);
    this.indexNameFormatter = indexNameFormatter;
    this.flushRequest = new Request.Builder()
        .url(baseUrl.resolve("/" + indexNameFormatter.catchAll() + "/_flush"))
        .post(RequestBody.create(APPLICATION_JSON, "")).build();
  }

  @Override public void accept(List<Span> spans, final Callback<Void> callback) {
    Buffer body = new Buffer();
    try {
      for (Span s : spans) indexSpan(s, body);
    } catch (IOException e) {
      callback.onError(e);
      return;
    }

    Call post = client.newCall(new Request.Builder()
        .url(baseUrl.resolve("/_bulk"))
        .post(RequestBody.create(APPLICATION_JSON, body.readByteString())).build());

    post.enqueue(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {
        callback.onError(e);
      }

      @Override public void onResponse(Call call, Response response){
        if (ElasticsearchRestStorage.FLUSH_ON_WRITES) {
          try {
            client.newCall(flushRequest).execute();
          } catch (IOException e) {
            callback.onError(e);
            return;
          }
        }
        callback.onSuccess(null);
      }
    });
  }

  // See https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
  void indexSpan(Span span, Buffer body) throws IOException {
    Long timestamp = guessTimestamp(span);
    long timestampMillis; // which index to store this span into
    if (timestamp != null) {
      timestampMillis = TimeUnit.MICROSECONDS.toMillis(timestamp);
    } else {
      timestampMillis = System.currentTimeMillis();
    }
    String index = indexNameFormatter.indexNameForTimestamp(timestampMillis);

    indexActionAndMetadataAdapter.toJson(body, new IndexActionAndMetadata(index));
    body.writeByte('\n');
    spanAdapter.toJson(body, span);
    body.writeByte('\n');
  }

  static final class IndexActionAndMetadata {
    final IndexMetadata index;

    IndexActionAndMetadata(String index) {
      this.index = new IndexMetadata(index);
    }
  }

  static final class IndexMetadata {
    final String _index;
    final String _type = ElasticsearchConstants.SPAN;

    IndexMetadata(String index) {
      _index = index;
    }
  }
}
