/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal

import com.linecorp.armeria.server.Server
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer

/**
 * Integration test suite for CORS configuration.
 *
 * Verifies that allowed-origins can be configured via properties (zipkin.query.allowed-origins).
 */
@SpringBootTest(classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.config.name=zipkin-server",
    "zipkin.query.allowed-origins=http://foo.example.com"
  ])
@RunWith(SpringRunner::class)
class ITZipkinServerCORS {
  @Autowired lateinit var server: Server
  @Autowired @Value("\${zipkin.query.allowed-origins}")
  lateinit var allowedOrigin: String
  internal val disallowedOrigin = "http://bar.example.com"

  /** Notably, javascript makes pre-flight requests, and won't POST spans if disallowed!  */
  @Test fun shouldAllowConfiguredOrigin_preflight() {
    shouldPermitPreflight(optionsForOrigin("GET", "/api/v2/traces", allowedOrigin))
    shouldPermitPreflight(optionsForOrigin("POST", "/api/v2/spans", allowedOrigin))
  }

  @Test fun shouldAllowConfiguredOrigin() {
    shouldAllowConfiguredOrigin(getTracesFromOrigin(allowedOrigin))
    shouldAllowConfiguredOrigin(postSpansFromOrigin(allowedOrigin))
  }

  @Test fun shouldDisallowOrigin() {
    shouldDisallowOrigin(getTracesFromOrigin(disallowedOrigin))
    shouldDisallowOrigin(postSpansFromOrigin(disallowedOrigin))
  }

  fun optionsForOrigin(method: String, path: String, origin: String): Response =
    Http.client.newCall(Request.Builder().url(Http.url(server, path)).headers(Headers.of(
      "Origin", origin,
      "access-control-request-method", method,
      "access-control-request-headers", "content-type"))
      .method("OPTIONS", null)
      .build()).execute()

  fun getTracesFromOrigin(origin: String): Response =
    Http.get(server, "/api/v2/traces", Headers.of("Origin", origin))

  fun postSpansFromOrigin(origin: String): Response =
    Http.post(server, "/api/v2/traces", null, "[]", Headers.of("Origin", origin))

  fun shouldPermitPreflight(response: Response) {
    assertThat(response.isSuccessful)
      .withFailMessage(response.toString())
      .isTrue()
    assertThat(response.header("vary")).contains("origin")
    assertThat(response.header("access-control-allow-origin")).contains(allowedOrigin)
    assertThat(response.header("access-control-allow-methods"))
      .contains(response.request().header("access-control-request-method"))
    assertThat(response.header("access-control-allow-credentials")).isNull()
    assertThat(response.header("access-control-allow-headers")).contains("content-type")
  }

  fun shouldAllowConfiguredOrigin(response: Response) {
    assertThat(response.header("vary")).contains("origin")
    assertThat(response.header("access-control-allow-origin"))
      .contains(response.request().header("origin"))
    assertThat(response.header("access-control-allow-credentials")).isNull()
    assertThat(response.header("access-control-allow-headers")).contains("content-type")
  }

  fun shouldDisallowOrigin(response: Response) {
    assertThat(response.header("vary")).isNull() // TODO: We used to set vary: origin
    assertThat(response.header("access-control-allow-credentials")).isNull()
    assertThat(response.header("access-control-allow-origin")).isNull()
    assertThat(response.header("access-control-allow-headers")).isNull()
  }
}
