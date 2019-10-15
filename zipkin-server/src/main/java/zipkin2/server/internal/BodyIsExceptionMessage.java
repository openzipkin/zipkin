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
package zipkin2.server.internal;

import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.linecorp.armeria.common.HttpStatus.BAD_REQUEST;
import static com.linecorp.armeria.common.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.linecorp.armeria.common.MediaType.ANY_TEXT_TYPE;

final class BodyIsExceptionMessage implements ExceptionHandlerFunction {
  static final Logger LOGGER = LoggerFactory.getLogger(BodyIsExceptionMessage.class);
  @Override
  public HttpResponse handleException(ServiceRequestContext ctx, HttpRequest req, Throwable cause) {
    if (req.method() == HttpMethod.POST && req.path().startsWith("/api/v")) {
      ZipkinHttpCollector.metrics.incrementMessagesDropped();
    }

    String message = cause.getMessage();
    if (message == null) message = cause.getClass().getSimpleName();
    if (cause instanceof IllegalArgumentException) {
      return HttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, message);
    } else {
      LOGGER.warn("Unexpected error handling request.", cause);

      return HttpResponse.of(INTERNAL_SERVER_ERROR, ANY_TEXT_TYPE, message);
    }
  }
}
