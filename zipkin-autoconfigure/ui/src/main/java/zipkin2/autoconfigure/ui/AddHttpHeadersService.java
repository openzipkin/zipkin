/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package zipkin2.autoconfigure.ui;

import com.linecorp.armeria.common.FilteredHttpResponse;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.HttpStatusClass;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingService;

/** Decorates a {@link Service} with additional HTTP headers upon success. */
final class AddHttpHeadersService extends SimpleDecoratingService<HttpRequest, HttpResponse> {

  final HttpHeaders toAdd;

  AddHttpHeadersService(Service<HttpRequest, HttpResponse> delegate, HttpHeaders toAdd) {
    super(delegate);
    this.toAdd = toAdd;
  }

  @Override public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    return new HttpEncodedResponse(delegate().serve(ctx, req), toAdd);
  }

  static final class HttpEncodedResponse extends FilteredHttpResponse {
    final HttpHeaders toAdd;

    HttpEncodedResponse(HttpResponse delegate, HttpHeaders toAdd) {
      super(delegate);
      this.toAdd = toAdd;
    }

    @Override protected HttpObject filter(HttpObject obj) {
      if (obj instanceof HttpHeaders) {
        HttpHeaders headers = (HttpHeaders) obj;
        HttpStatus status = headers.status();
        if (status != null && status.codeClass() == HttpStatusClass.SUCCESS) {
          headers.add(toAdd);
        }
      }
      return obj;
    }
  }
}
