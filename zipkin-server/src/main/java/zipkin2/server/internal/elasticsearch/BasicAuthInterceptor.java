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

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import com.squareup.moshi.JsonReader;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import okio.Okio;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

/**
 * Adds basic auth username and password to every request per
 * https://www.elastic.co/guide/en/x-pack/current/how-security-works.html
 */
final class BasicAuthInterceptor extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  private String basicCredentials;

  BasicAuthInterceptor(
    Client<HttpRequest, HttpResponse> client,
    ZipkinElasticsearchStorageProperties es) {
    super(client);
    String token = es.getUsername() + ':' + es.getPassword();
    basicCredentials = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  @Override public HttpResponse execute(ClientRequestContext ctx, HttpRequest req)
    throws Exception {
    ctx.addAdditionalRequestHeader(HttpHeaderNames.AUTHORIZATION, basicCredentials);
    return HttpResponse.from(
      delegate().execute(ctx, req).aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc())
        .thenApply(msg -> {
          HttpData content = msg.content();
          try {
            if (!msg.status().equals(HttpStatus.FORBIDDEN) || content.isEmpty()) {
              return HttpResponse.of(msg);
            }
            final ByteBuffer buf;
            if (content instanceof ByteBufHolder) {
              buf = ((ByteBufHolder) content).content().nioBuffer();
            } else {
              buf = ByteBuffer.wrap(content.array());
            }
            try {
              JsonReader message = enterPath(JsonReader.of(
                Okio.buffer(Okio.source(new ByteBufferBackedInputStream(buf)))), "message");
              if (message != null) throw new IllegalStateException(message.nextString());
            } catch (IOException e) {
              Exceptions.throwUnsafely(e);
              throw new UncheckedIOException(e);  // unreachable
            }
            throw new IllegalStateException(msg.toString());
          } finally {
            ReferenceCountUtil.safeRelease(content);
          }
        }));
  }
}
