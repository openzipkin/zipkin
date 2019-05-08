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

import com.jayway.jsonpath.JsonPath
import com.linecorp.armeria.server.Server
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.TestObjects.LOTS_OF_SPANS
import zipkin2.codec.SpanBytesEncoder

/**
 * We cannot clear the micrometer registry easily, so we have recreate the spring context. This is
 * extremely slow, so please only add tests that require isolation here.
 */
@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server"]
)
@RunWith(SpringRunner::class)
@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
class ITZipkinMetricsHealthDirty {
  @Autowired lateinit var server: Server
  @Autowired lateinit var registry: PrometheusMeterRegistry

  @Test fun writeSpans_updatesMetrics() {
    val spans = listOf(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2])
    val body = SpanBytesEncoder.JSON_V2.encodeList(spans)
    val messagesCount = registry.counter("zipkin_collector.messages", "transport", "http").count()
    val bytesCount = registry.counter("zipkin_collector.bytes", "transport", "http").count()
    val spansCount = registry.counter("zipkin_collector.spans", "transport", "http").count()
    Http.post(server, "/api/v2/spans", body = body)
    Http.post(server, "/api/v2/spans", body = body)

    val json = Http.getAsString(server, "/metrics")

    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(messagesCount + 2.0)
    assertThat(readDouble(json, "$.['counter.zipkin_collector.bytes.http']"))
      .isEqualTo(bytesCount + body.size * 2)
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_bytes.http']"))
      .isEqualTo(body.size.toDouble())
    assertThat(readDouble(json, "$.['counter.zipkin_collector.spans.http']"))
      .isEqualTo(spansCount + spans.size * 2)
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_spans.http']"))
      .isEqualTo(spans.size.toDouble())
  }

  @Test fun writeSpans_malformedUpdatesMetrics() {
    val body = byteArrayOf('h'.toByte(), 'e'.toByte(), 'l'.toByte(), 'l'.toByte(), 'o'.toByte())
    val messagesCount = registry.counter("zipkin_collector.messages", "transport", "http").count()
    val messagesDroppedCount =
      registry.counter("zipkin_collector.messages_dropped", "transport", "http").count()
    Http.post(server, "/api/v2/spans", body = body)

    val json = Http.getAsString(server, "/metrics")

    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(messagesCount + 1)
    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages_dropped.http']"))
      .isEqualTo(messagesDroppedCount + 1)
  }

  fun readDouble(json: String, jsonPath: String): Double = JsonPath.compile(jsonPath).read(json)
}
