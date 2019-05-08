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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.Endpoint
import zipkin2.Span
import zipkin2.TestObjects.TODAY
import zipkin2.TestObjects.TRACE
import zipkin2.TestObjects.UTF_8
import zipkin2.codec.SpanBytesEncoder
import zipkin2.storage.InMemoryStorage

@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server"]
)
@RunWith(SpringRunner::class)
class ITZipkinServer {
  @Autowired lateinit var server: Server
  @Autowired lateinit var storage: InMemoryStorage
  @Before fun clearStorage() = storage.clear()

  @Test fun getTrace() {
    storage.accept(TRACE).execute()

    val response = Http.get(server, "/api/v2/trace/" + TRACE[0].traceId())
    assertThat(response.isSuccessful).isTrue()

    assertThat(response.body()!!.bytes())
      .containsExactly(*SpanBytesEncoder.JSON_V2.encodeList(TRACE))
  }

  @Test fun tracesQueryRequiresNoParameters() {
    storage.accept(TRACE).execute()

    val response = Http.get(server, "/api/v2/traces")
    assertThat(response.isSuccessful).isTrue()
    assertThat(response.body()!!.string())
      .isEqualTo("[" + String(SpanBytesEncoder.JSON_V2.encodeList(TRACE), UTF_8) + "]")
  }

  @Test fun v2WiresUp() {
    assertThat(Http.get(server, "/api/v2/services").isSuccessful)
      .isTrue()
  }

  @Test fun doesntSetCacheControlOnNameEndpointsWhenLessThan4Services() {
    storage.accept(TRACE).execute()

    assertThat(Http.get(server, "/api/v2/services").header("Cache-Control"))
      .isNull()

    assertThat(Http.get(server, "/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isNull()

    assertThat(Http.get(server, "/api/v2/remoteServices?serviceName=web").header("Cache-Control"))
      .isNull()
  }

  @Test fun spanNameQueryWorksWithNonAsciiServiceName() {
    assertThat(Http.get(server, "/api/v2/spans?serviceName=个人信息服务").code())
      .isEqualTo(200)
  }

  @Test fun remoteServiceNameQueryWorksWithNonAsciiServiceName() {
    assertThat(Http.get(server, "/api/v2/remoteServices?serviceName=个人信息服务").code())
      .isEqualTo(200)
  }

  @Test fun setsCacheControlOnNameEndpointsWhenMoreThan3Services() {
    val services = listOf("foo", "bar", "baz", "quz")
    for (i in services.indices) {
      storage.accept(listOf(
        Span.newBuilder().traceId("a").id((i + 1).toLong()).timestamp(TODAY).name("whopper")
          .localEndpoint(Endpoint.newBuilder().serviceName(services[i]).build())
          .remoteEndpoint(Endpoint.newBuilder().serviceName(services[i] + 1).build())
          .build()
      )).execute()
    }

    assertThat(Http.get(server, "/api/v2/services").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate")

    assertThat(Http.get(server, "/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate")

    assertThat(Http.get(server, "/api/v2/remoteServices?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate")

    // Check that the response is alphabetically sorted.
    assertThat(Http.getAsString(server, "/api/v2/services"))
      .isEqualTo("[\"bar\",\"baz\",\"foo\",\"quz\"]")
  }

  @Test fun shouldAllowAnyOriginByDefault() {
    val response = Http.get(server, "/api/v2/traces", Headers.of(
      "Origin", "http://foo.example.com"
    ))

    assertThat(response.isSuccessful).isTrue()
    assertThat(response.header("vary")).isNull()
    assertThat(response.header("access-control-allow-credentials")).isNull()
    assertThat(response.header("access-control-allow-origin")).contains("*")
  }

  @Test fun forwardsApiForUi() {
    assertThat(Http.get(server, "/zipkin/api/v2/traces").isSuccessful).isTrue()
    assertThat(Http.get(server, "/zipkin/api/v2/traces").isSuccessful).isTrue()
  }

  /** Simulate a proxy which forwards / to zipkin as opposed to resolving / -> /zipkin first  */
  @Test fun redirectedHeaderUsesOriginalHostAndPort() {
    val response = Http.get(server, "/", Headers.of(
      "Host", "zipkin.com",
      "X-Forwarded-Proto", "https",
      "X-Forwarded-Port", "444"
    ))

    // Redirect header should be the proxy, not the backed IP/port
    assertThat(response.header("Location"))
      .isEqualTo("/zipkin/")
  }

  @Test fun infoEndpointIsAvailable() {
    val response = Http.get(server, "/info")
    assertThat(response.code()).isEqualTo(307)
    assertThat(response.header("location")).isEqualTo("/actuator/info")

    assertThat(Http.get(server, "/actuator/info").isSuccessful).isTrue()
  }
}
