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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Adds basic auth username and password to every request per https://www.elastic.co/guide/en/x-pack/current/how-security-works.html
 */
final class BasicAuthInterceptor extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  final String basicCredentials;

  BasicAuthInterceptor(Client<HttpRequest, HttpResponse> client, String username, String password) {
    super(client);
    if (username == null) throw new NullPointerException("username == null");
    if (password == null) throw new NullPointerException("password == null");
    String token = username + ':' + password;
    basicCredentials = "Basic " + Base64.getEncoder().encodeToString(token.getBytes(UTF_8));
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
    ctx.addAdditionalRequestHeader(HttpHeaderNames.AUTHORIZATION, basicCredentials);
    return delegate().execute(ctx, req);
  }
}
