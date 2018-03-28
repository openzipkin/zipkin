/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server.internal;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspired by Netty's CorsHandler and driven by tests in https://github.com/rs/cors
 *
 * <p>This implementation simplified based on needs of the zipkin UI. For example, we don't need
 * sophisticated expressions, nor do we need to send back non-default response headers
 * (Access-Control-Expose-Headers). Finally, we don't have authorization
 * (Access-Control-Allow-Credentials) at the moment. When these assumptions change, we can complete
 * the implementation.
 */

final class CorsHandler implements HttpHandler, HandlerWrapper {
  private static Logger logger = Logger.getLogger(CorsHandler.class.getName());

  static final HttpString
    OPTIONS = HttpString.tryFromString("OPTIONS"),
    ORIGIN = HttpString.tryFromString("origin"),
    VARY = HttpString.tryFromString("vary"),
    ACCESS_CONTROL_ALLOW_METHODS = HttpString.tryFromString("access-control-allow-methods"),
    ACCESS_CONTROL_ALLOW_HEADERS = HttpString.tryFromString("access-control-allow-headers"),
    ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("access-control-allow-origin"),
    ACCESS_CONTROL_REQUEST_METHOD = HttpString.tryFromString("access-control-request-method"),
    ACCESS_CONTROL_REQUEST_HEADERS = HttpString.tryFromString("access-control-request-headers");

  final List<String> allowedOrigins;
  final List<String> allowedHeaders;
  final boolean wildcardOrigin;
  HttpHandler next;

  CorsHandler(String allowedOrigins) {
    this.allowedOrigins = Arrays.asList(allowedOrigins.split(","));
    this.allowedHeaders = Arrays.asList(
      "accept",
      "content-type",
      "content-encoding",
      "origin"
    );
    this.wildcardOrigin = this.allowedOrigins.contains("*");
  }

  @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
    if (isPreflightRequest(exchange)) {
      handlePreflight(exchange);
      exchange.getResponseSender().close();
      return;
    }

    if (!validateOrigin(exchange)) {
      exchange.setStatusCode(403).getResponseSender().send("CORS error\n");
      return;
    }

    next.handleRequest(exchange);
  }

  /** Statically allows headers used by the api */
  void handlePreflight(HttpServerExchange exchange) {
    HeaderMap requestHeaders = exchange.getRequestHeaders();
    String origin = requestHeaders.getFirst(ORIGIN);
    String method = requestHeaders.getFirst(ACCESS_CONTROL_REQUEST_METHOD);
    String requestedHeaders = requestHeaders.getFirst(ACCESS_CONTROL_REQUEST_HEADERS);
    HeaderMap responseHeaders = exchange.getResponseHeaders();

    responseHeaders.put(VARY,
      "origin,access-control-request-method,access-control-request-headers");
    if (
      ("POST".equals(method) || "GET".equals(method))
        && requestedHeadersAllowed(requestedHeaders)
        && setOrigin(origin, responseHeaders)
      ) {
      responseHeaders.put(ACCESS_CONTROL_ALLOW_METHODS, method);
      if (requestedHeaders != null) {
        responseHeaders.put(ACCESS_CONTROL_ALLOW_HEADERS, requestedHeaders);
      }
    }
  }

  boolean requestedHeadersAllowed(String requestedHeaders) {
    if (requestedHeaders == null) return true;
    StringBuilder next = new StringBuilder();
    for (int i = 0, length = requestedHeaders.length(); i < length; i++) {
      char c = requestedHeaders.charAt(i);
      if (c == ' ') continue;
      if (c >= 'A' && c <= 'Z') c += 'a' - 'A'; // lowercase
      if (c != ',') next.append(c);
      if (c == ',' || i + 1 == length) {
        String toTest = next.toString();
        if (!allowedHeaders.contains(toTest)) {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(toTest + " is not an allowed header: " + allowedHeaders);
          }
          return false;
        }
        next.setLength(0);
      }
    }
    return true;
  }

  boolean validateOrigin(HttpServerExchange exchange) {
    HeaderMap responseHeaders = exchange.getResponseHeaders();
    responseHeaders.put(VARY, "origin");
    String origin = exchange.getRequestHeaders().getFirst(ORIGIN);
    if (origin == null) return true; // just vary
    return setOrigin(origin, responseHeaders);
  }

  private static boolean isPreflightRequest(HttpServerExchange exchange) {
    HeaderMap headers = exchange.getRequestHeaders();
    return exchange.getRequestMethod().equals(OPTIONS) &&
      headers.contains(ORIGIN) && headers.contains(ACCESS_CONTROL_REQUEST_METHOD);
  }

  private boolean setOrigin(String origin, HeaderMap responseHeaders) {
    if ("null".equals(origin)) {
      responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, "null");
      return true;
    }
    if (wildcardOrigin) {
      responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      return true;
    } else if (allowedOrigins.contains(origin)) {
      responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      return true;
    }
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(origin + " is not an allowed origin: " + allowedOrigins);
    }
    return false;
  }

  @Override public HttpHandler wrap(HttpHandler handler) {
    this.next = handler;
    return this;
  }
}
