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
package zipkin.junit;

import java.io.IOException;
import java.util.List;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import zipkin.Codec;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

/**
 * Implements the span consumer interface by forwarding requests over http.
 */
final class HttpSpanConsumer implements AsyncSpanConsumer {
  private final OkHttpClient client;
  private final HttpUrl baseUrl;

  HttpSpanConsumer(OkHttpClient client, HttpUrl baseUrl) {
    this.client = client;
    this.baseUrl = baseUrl;
  }

  @Override
  public void accept(List<Span> spans, final Callback<Void> callback) {
    byte[] spansInJson = Codec.JSON.writeSpans(spans);
    client.newCall(new Request.Builder()
        .url(baseUrl.resolve("/api/v1/spans"))
        .post(RequestBody.create(MediaType.parse("application/json"), spansInJson)).build()
    ).enqueue(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {
        callback.onError(e);
      }

      @Override public void onResponse(Call call, Response response) throws IOException {
        callback.onSuccess(null);
      }
    });
  }
}
