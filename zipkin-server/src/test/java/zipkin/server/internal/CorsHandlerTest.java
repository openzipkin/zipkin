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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

// tests inspired by https://github.com/rs/cors/blob/master/cors_test.go
public class CorsHandlerTest {
  CorsHandler handler = new CorsHandler("*");

  /** Whenever we process ensure a change in origin invalidates cache */
  @Test public void validateOrigin_defaultsToVaryOrigin() {
    boolean valid = validateOrigin(
      "get",
      ImmutableMap.of(),
      ImmutableMap.of("vary", "origin")
    );

    assertThat(valid).isTrue();
  }

  /** Whenever we permit all, return that vs the requested origin */
  @Test public void validateOrigin_returnsWildcard() {
    boolean valid = validateOrigin(
      "get",
      ImmutableMap.of("Origin", "http://foobar.com"),
      ImmutableMap.of(
        "vary", "origin",
        "access-control-allow-origin", "*"
      )
    );

    assertThat(valid).isTrue();
  }

  @Test public void validateOrigin_configuredOrigin() {
    handler = new CorsHandler("http://foobar.com");
    boolean valid = validateOrigin(
      "get",
      ImmutableMap.of("Origin", "http://foobar.com"),
      ImmutableMap.of(
        "vary", "origin",
        "access-control-allow-origin", "http://foobar.com"
      )
    );

    assertThat(valid).isTrue();
  }

  @Test public void validateOrigin_disallowedOrigin() {
    handler = new CorsHandler("http://foobar.com");
    boolean valid = validateOrigin(
      "get",
      ImmutableMap.of("Origin", "http://barbaz.com"),
      ImmutableMap.of("vary", "origin")
    );

    assertThat(valid).isFalse();
  }

  @Test public void handlePreflight_allowsPOST() {
    handler = new CorsHandler("http://foobar.com");
    handlePreflight(
      ImmutableMap.of(
        "Origin", "http://foobar.com",
        "Access-Control-Request-Method", "POST"),
      ImmutableMap.of(
        "vary", "origin,access-control-request-method,access-control-request-headers",
        "access-control-allow-origin", "http://foobar.com",
        "access-control-allow-methods", "POST"
      )
    );
  }

  @Test public void handlePreflight_disallowsDELETE() {
    handler = new CorsHandler("http://foobar.com");
    handlePreflight(
      ImmutableMap.of(
        "Origin", "http://foobar.com",
        "Access-Control-Request-Method", "DELETE"),
      ImmutableMap.of(
        "vary", "origin,access-control-request-method,access-control-request-headers"
      )
    );
  }

  @Test public void handlePreflight_allowsContentType() {
    handler = new CorsHandler("http://foobar.com");
    handlePreflight(
      ImmutableMap.of(
        "Origin", "http://foobar.com",
        "Access-Control-Request-Method", "POST",
        "Access-Control-Request-Headers", "Content-Type, Content-Encoding"),
      ImmutableMap.of(
        "vary", "origin,access-control-request-method,access-control-request-headers",
        "access-control-allow-origin", "http://foobar.com",
        "access-control-allow-methods", "POST",
        "access-control-allow-headers", "Content-Type, Content-Encoding"
      )
    );
  }

  @Test public void handlePreflight_disallowsAuthorization() {
    handler = new CorsHandler("http://foobar.com");
    handlePreflight(
      ImmutableMap.of(
        "Origin", "http://foobar.com",
        "Access-Control-Request-Method", "POST",
        "Access-Control-Request-Headers", "Content-Type, Authorization"),
      ImmutableMap.of(
        "vary", "origin,access-control-request-method,access-control-request-headers"
      )
    );
  }

  @Test public void handlePreflight_allowsOrigin() {
    handler = new CorsHandler("http://foobar.com");
    handlePreflight(
      ImmutableMap.of(
        "Origin", "http://foobar.com",
        "Access-Control-Request-Method", "POST",
        "Access-Control-Request-Headers", "origin"),
      ImmutableMap.of(
        "vary", "origin,access-control-request-method,access-control-request-headers",
        "access-control-allow-origin", "http://foobar.com",
        "access-control-allow-methods", "POST",
        "access-control-allow-headers", "origin"
      )
    );
  }

  boolean validateOrigin(String method, Map<String, String> requestHeaders,
    Map<String, String> responseHeaders) {
    return handle(handler::validateOrigin, method, requestHeaders, responseHeaders);
  }

  boolean handlePreflight(Map<String, String> requestHeaders, Map<String, String> responseHeaders) {
    return handle(exchange -> {
      handler.handlePreflight(exchange);
      return true;
    }, "OPTIONS", requestHeaders, responseHeaders);
  }

  boolean handle(Function<HttpServerExchange, Boolean> func, String method,
    Map<String, String> requestHeaders,
    Map<String, String> responseHeaders) {
    HttpServerExchange exchange = new HttpServerExchange(null);
    exchange.setRequestMethod(HttpString.tryFromString(method));
    requestHeaders.forEach(
      (k, v) -> exchange.getRequestHeaders().put(HttpString.tryFromString(k), v));
    boolean result = func.apply(exchange);
    assertThat(exchange.getResponseHeaders())
      .extracting(
        h -> (Map.Entry<String, String>) entry(h.getHeaderName().toString(), h.getFirst()))
      .containsOnlyElementsOf(responseHeaders.entrySet());
    return result;
  }
}
