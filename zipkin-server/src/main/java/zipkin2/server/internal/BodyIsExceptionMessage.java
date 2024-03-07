/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.internal.ClosedComponentException;

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
      // Don't fill logs with exceptions about closed components.
      if (!(cause instanceof ClosedComponentException)) {
        LOGGER.warn("Unexpected error handling {} {}", req.method(), req.path());
      }

      return HttpResponse.of(INTERNAL_SERVER_ERROR, ANY_TEXT_TYPE, message);
    }
  }
}
