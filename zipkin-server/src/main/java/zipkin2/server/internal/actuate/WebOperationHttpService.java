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
package zipkin2.server.internal.actuate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperation;

final class WebOperationHttpService implements HttpService {
  final ObjectMapper objectMapper;
  final WebOperation operation;

  WebOperationHttpService(ObjectMapper objectMapper, WebOperation operation) {
    this.objectMapper = objectMapper;
    this.operation = operation;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    return HttpResponse.from(serve0(ctx, req));
  }

  private CompletionStage<HttpResponse> serve0(ServiceRequestContext ctx, HttpRequest req) {
    if (req.method() == HttpMethod.POST) {
      return req.aggregate().thenApply(msg -> {
        throw new UnsupportedOperationException("reading body not yet supported");
      });
    }
    return CompletableFuture.completedFuture(null).thenApply(nil -> invoke(ctx, req));
  }

  HttpResponse invoke(ServiceRequestContext ctx, HttpRequest req) {
    try {
      return handleResult(
        operation.invoke(new InvocationContext(SecurityContext.NONE, Collections.emptyMap())),
        req.method());
    } catch (Throwable ex) {
      return HttpResponse.ofFailure(ex);
    }
  }

  HttpResponse handleResult(Object result, HttpMethod httpMethod) throws IOException {
    if (result == null) {
      return HttpResponse.of(
        httpMethod != HttpMethod.GET ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    if (!(result instanceof WebEndpointResponse)) {
      return HttpResponse.of(
        HttpStatus.OK,
        MediaType.JSON,
        objectMapper.writeValueAsBytes(result)
      );
    }
    WebEndpointResponse<?> response = (WebEndpointResponse<?>) result;
    return HttpResponse.of(
      HttpStatus.valueOf(response.getStatus()),
      MediaType.JSON,
      objectMapper.writeValueAsBytes(response.getBody())
    );
  }
}
