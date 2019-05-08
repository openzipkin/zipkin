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

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import com.linecorp.armeria.server.Server
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.TestObjects.LOTS_OF_SPANS
import zipkin2.TestObjects.UTF_8
import zipkin2.codec.SpanBytesEncoder
import java.util.ArrayList

@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server"]
)
@RunWith(SpringRunner::class)
class ITZipkinMetricsHealth {
  @Autowired lateinit var server: Server
  @Autowired lateinit var registry: PrometheusMeterRegistry

  @Test fun healthIsOK() {
    assertThat(Http.get(server, "/health").isSuccessful)
      .isTrue()

    // ensure we don't track health in prometheus
    assertThat(scrape())
      .doesNotContain("health")
  }

  @Test fun metricsIsOK() {
    assertThat(Http.get(server, "/metrics").isSuccessful)
      .isTrue()

    // ensure we don't track metrics in prometheus
    assertThat(scrape())
      .doesNotContain("metrics")
  }

  @Test fun actuatorIsOK() {
    assertThat(Http.get(server, "/actuator").isSuccessful)
      .isTrue()

    // ensure we don't track actuator in prometheus
    assertThat(scrape())
      .doesNotContain("actuator")
  }

  @Test fun prometheusIsOK() {
    val response = Http.get(server, "/prometheus")
    assertThat(response.code()).isEqualTo(307)
    assertThat(response.header("location")).isEqualTo("/actuator/prometheus")

    assertThat(Http.get(server, "/actuator/prometheus").isSuccessful)
      .isTrue()

    // ensure we don't track prometheus, UI requests in prometheus
    assertThat(scrape())
      .doesNotContain("prometheus")
      .doesNotContain("uri=\"/zipkin")
      .doesNotContain("uri=\"/\"")
  }

  @Test fun notFound_prometheus() {
    assertThat(Http.get(server, "/doo-wop").isSuccessful)
      .isFalse()

    assertThat(scrape())
      .contains("uri=\"NOT_FOUND\"")
      .doesNotContain("uri=\"/doo-wop")
  }

  @Test fun redirected_prometheus() {
    assertThat(Http.get(server, "/").code())
      .isEqualTo(302) // redirect

    assertThat(scrape())
      .contains("uri=\"REDIRECTION\"")
      .doesNotContain("uri=\"/\"")
  }

  @Test fun apiTemplate_prometheus() {
    val spans = listOf(LOTS_OF_SPANS[0])
    val body = SpanBytesEncoder.JSON_V2.encodeList(spans)
    assertThat(Http.post(server, "/api/v2/spans", body = body).isSuccessful)
      .isTrue()

    assertThat(Http.get(server, "/api/v2/trace/" + LOTS_OF_SPANS[0].traceId()).isSuccessful)
      .isTrue()

    assertThat(scrape())
      .contains("uri=\"/api/v2/trace/{traceId}\"")
      .doesNotContain(LOTS_OF_SPANS[0].traceId())
  }

  @Test fun forwardedRoute_prometheus() {
    assertThat(Http.get(server, "/zipkin/api/v2/services").isSuccessful)
      .isTrue()

    assertThat(scrape())
      .contains("uri=\"/api/v2/services\"")
      .doesNotContain("uri=\"/zipkin/api/v2/services\"")
  }

  internal fun scrape(): String {
    Thread.sleep(100)
    return registry.scrape()
  }

  /** Makes sure the prometheus filter doesn't count twice  */
  @Test fun writeSpans_updatesPrometheusMetrics() {
    val spans = listOf(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2])
    val body = SpanBytesEncoder.JSON_V2.encodeList(spans)

    Http.post(server, "/api/v2/spans", body = body)
    Http.post(server, "/api/v2/spans", body = body)

    Thread.sleep(100) // sometimes travis flakes getting the "http.server.requests" timer
    val messagesCount = registry.counter("zipkin_collector.spans", "transport", "http").count()
    // Get the http count from the registry and it should match the summation previous count
    // and count of calls below
    val httpCount = registry
      .find("http.server.requests")
      .tag("uri", "/api/v2/spans")
      .timer()!!
      .count()

    // ensure unscoped counter does not exist
    assertThat(scrape())
      .doesNotContain("zipkin_collector_spans_total $messagesCount")
      .contains("zipkin_collector_spans_total{transport=\"http\",} $messagesCount")
      .contains(
        "http_server_requests_seconds_count{method=\"POST\",status=\"202\",uri=\"/api/v2/spans\",} $httpCount")
  }

  @Test fun readsHealth() {
    val json = Http.getAsString(server, "/health")
    assertThat(readString(json, "$.status"))
      .isIn("UP", "DOWN", "UNKNOWN")
    assertThat(readString(json, "$.zipkin.status"))
      .isIn("UP", "DOWN", "UNKNOWN")
  }

  @Test fun writesSpans_readMetricsFormat() {
    val span = "zipkin".toByteArray(UTF_8)
    val spans = listOf(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2])
    val body = SpanBytesEncoder.JSON_V2.encodeList(spans)
    Http.post(server, "/api/v2/spans", body = body)
    Http.post(server, "/api/v2/spans", body = body)
    Http.post(server, "/api/v2/spans", body = span)
    Thread.sleep(1500)

    assertThat(readJson(Http.getAsString(server, "/metrics")))
      .containsExactlyInAnyOrder(
        "gauge.zipkin_collector.message_spans.http",
        "gauge.zipkin_collector.message_bytes.http", "counter.zipkin_collector.messages.http",
        "counter.zipkin_collector.bytes.http", "counter.zipkin_collector.spans.http",
        "counter.zipkin_collector.messages_dropped.http",
        "counter.zipkin_collector.spans_dropped.http"
      )
  }

  fun readString(json: String, jsonPath: String): String = JsonPath.compile(jsonPath).read(json)

  fun readJson(json: String): List<*> {
    val mapper = ObjectMapper()
    val jsonNode = mapper.readTree(json)
    val fieldsList = ArrayList<String>()
    jsonNode.fieldNames().forEachRemaining { fieldsList.add(it) }
    return fieldsList
  }
}
