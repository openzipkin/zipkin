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
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A simple decorator to record raw content strings into HTTP logs. By default, Armeria only logs
 * RPC request / responses, not raw HTTP client content.
 */
// TODO: unit test coverage
final class RawContentLoggingClient extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  RawContentLoggingClient(Client<HttpRequest, HttpResponse> delegate) {
    super(delegate);
  }

  @Override public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) {
    // We aggregate with pooled objects to keep things out of the heap as much as possible. For
    // example, we want to keep a large response pooled, only copying small portions during JSON
    // parsing, and copying once into a String for logging, while if we didn't aggregate with pooled
    // objects, we would copy the entire response onto the heap, then once again into a String.
    return HttpResponse.from(
      req.aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc())
        .thenCompose(aggregatedReq -> {
          if (!aggregatedReq.content().isEmpty()) {
            ctx.logBuilder().requestContent(aggregatedReq.contentUtf8(), null);
          }
          try {
            return delegate().execute(ctx, HttpRequest.of(aggregatedReq))
              .aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc());
          } catch (Exception e) {
            CompletableFuture<AggregatedHttpResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
          }
        })
        .thenApply(aggregatedResp -> {
          if (!aggregatedResp.content().isEmpty()) {
            ctx.logBuilder().responseContent(aggregatedResp.contentUtf8(), null);
          }
          return HttpResponse.of(aggregatedResp);
        }));
  }
}
