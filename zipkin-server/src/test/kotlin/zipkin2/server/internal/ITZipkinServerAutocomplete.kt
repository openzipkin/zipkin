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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.Span
import zipkin2.TestObjects.TODAY
import zipkin2.codec.SpanBytesEncoder

/**
 * Integration test suite for autocomplete tags.
 *
 * Verifies that the whitelist of key can be configured via "zipkin.storage.autocomplete-keys".
 */
@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.config.name=zipkin-server",
    "zipkin.storage.autocomplete-keys=environment,clnt/finagle.version"
  ]
)
@RunWith(SpringRunner::class)
class ITZipkinServerAutocomplete {
  @Autowired lateinit var server: Server
  val values = "/api/v2/autocompleteValues"

  @Test fun setsCacheControlOnAutocompleteKeysEndpoint() {
    assertThat(Http.get(server, "/api/v2/autocompleteKeys").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate")
  }

  @Test fun setsCacheControlOnAutocompleteEndpointWhenMoreThan3Values() {
    assertThat(Http.get(server, "$values?key=environment").header("Cache-Control"))
      .isNull()
    assertThat(Http.get(server, "$values?key=clnt/finagle.version").header("Cache-Control"))
      .isNull()

    for (i in 0..3) {
      Http.post(server, "/api/v2/spans", body = SpanBytesEncoder.JSON_V2.encodeList(listOf(
        Span.newBuilder().traceId("a").id((i + 1).toLong()).timestamp(TODAY).name("whopper")
          .putTag("clnt/finagle.version", "6.45.$i").build()
      )))
    }

    assertThat(Http.get(server, "$values?key=environment").header("Cache-Control"))
      .isNull()
    assertThat(Http.get(server, "$values?key=clnt/finagle.version").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate")
  }
}
