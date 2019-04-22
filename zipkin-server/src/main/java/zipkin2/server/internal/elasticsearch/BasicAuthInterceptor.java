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
package zipkin2.server.internal.elasticsearch;

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/**
 * Adds basic auth username and password to every request per
 * https://www.elastic.co/guide/en/x-pack/current/how-security-works.html
 */
final class BasicAuthInterceptor implements Interceptor {

  private String basicCredentials;

  BasicAuthInterceptor(ZipkinElasticsearchStorageProperties es) {
    basicCredentials = Credentials.basic(es.getUsername(), es.getPassword());
  }

  @Override
  public Response intercept(Chain chain) throws IOException {

    Request input = chain.request();
    Request requestWithCredentials = appendBasicAuthHeaderParameters(input);
    Response response = chain.proceed(requestWithCredentials);
    if (response.code() == 403) {
      try (ResponseBody body = response.body()) {
        JsonReader message = enterPath(JsonReader.of(body.source()), "message");
        if (message != null) throw new IllegalStateException(message.nextString());
      }
      throw new IllegalStateException(response.toString());
    }
    return response;
  }

  private Request appendBasicAuthHeaderParameters(Request input) {

    Request.Builder builder = input.newBuilder();
    return builder.header("authorization", basicCredentials).build();
  }
}
