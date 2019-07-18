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
package zipkin2.elasticsearch.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/**
 * Adds basic auth username and password to every request per https://www.elastic.co/guide/en/x-pack/current/how-security-works.html
 */
public final class BasicAuthInterceptor extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  public static BasicAuthInterceptor create(
    Client<HttpRequest, HttpResponse> client, String username, String password) {
    return new BasicAuthInterceptor(client, username, password);
  }

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
    return HttpResponse.from(delegate().execute(ctx, req)
      .aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc())
      .thenApplyAsync(msg -> {
        HttpData content = msg.content();
        if (!msg.status().equals(HttpStatus.FORBIDDEN) || content.isEmpty()) {
          return HttpResponse.of(msg);
        }
        final ByteBuf buf;
        if (content instanceof ByteBufHolder) {
          buf = ((ByteBufHolder) content).content();
        } else {
          buf = Unpooled.wrappedBuffer(content.array());
        }
        try (ByteBufInputStream stream = new ByteBufInputStream(buf, true)) {
          try {
            JsonParser message = enterPath(JsonAdapters.jsonParser(stream), "message");
            if (message != null) throw new IllegalStateException(message.getValueAsString());
          } catch (IOException e) {
            Exceptions.throwUnsafely(e);
            throw new UncheckedIOException(e);  // unreachable
          }
          throw new IllegalStateException(msg.toString());
        } catch (IOException e) {
          throw new AssertionError("Couldn't close memory stream", e);
        }
      }, ctx.contextAwareExecutor()));
  }
}
