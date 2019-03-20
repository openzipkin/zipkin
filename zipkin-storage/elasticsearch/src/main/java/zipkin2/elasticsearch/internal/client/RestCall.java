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
package zipkin2.elasticsearch.internal.client;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import zipkin2.Call;
import zipkin2.Callback;

import java.io.IOException;

public class RestCall<V> extends Call<V> {
  private final RestClient client;
  private final Request request;
  private final ResponseConverter<V> responseConverter;

  public RestCall(RestClient client, Request request, ResponseConverter<V> responseConverter) {
    this.client = client;
    this.request = request;
    this.responseConverter = responseConverter;
  }

  @Override
  public V execute() throws IOException {
    return responseConverter.convert(client.performRequest(request));
  }

  @Override
  public void enqueue(Callback<V> callback) {
    client.performRequestAsync(request, new CallbackResponseListener<>(responseConverter, callback));
  }

  /**
   * Zipkin callback / Elasticsearch response listener adapter
   */
  private static class CallbackResponseListener<V> implements ResponseListener {
    private final ResponseConverter<V> responseConverter;
    private final Callback<V> callback;

    private CallbackResponseListener(ResponseConverter<V> responseConverter, Callback<V> callback) {
      this.responseConverter = responseConverter;
      this.callback = callback;
    }

    @Override
    public void onSuccess(Response response) {
      try {
        V result = responseConverter.convert(response);
        callback.onSuccess(result);
      } catch (IOException e) {
        callback.onError(e);
      }
    }

    @Override
    public void onFailure(Exception e) {
      callback.onError(e);
    }
  }

  @Override
  public void cancel() {
    // TODO
  }

  @Override
  public boolean isCanceled() {
    return false; // TODO
  }

  @Override
  public Call<V> clone() {
    return new RestCall<>(client, request, responseConverter);
  }
}
