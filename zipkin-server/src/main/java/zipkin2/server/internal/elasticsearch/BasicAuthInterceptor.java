/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import java.util.Objects;

/**
 * Adds basic auth username and password to every request.
 *
 * <p>Ref: <a href="https://www.elastic.co/guide/en/x-pack/current/how-security-works.html"> How
 * Elasticsearch security works</a></p>
 */
final class BasicAuthInterceptor extends SimpleDecoratingHttpClient {

  final BasicCredentials basicCredentials;

  BasicAuthInterceptor(HttpClient client, BasicCredentials basicCredentials) {
    super(client);
    this.basicCredentials = basicCredentials;
  }

  @Override
  public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
    String credentials = basicCredentials.getCredentials();
    if (credentials != null) {
      ctx.addAdditionalRequestHeader(HttpHeaderNames.AUTHORIZATION, credentials);
    }
    return unwrap().execute(ctx, req);
  }
}
