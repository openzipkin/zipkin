/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;

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
