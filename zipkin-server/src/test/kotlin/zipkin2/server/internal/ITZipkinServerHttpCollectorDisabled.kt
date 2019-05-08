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

/**
 * Query-only builds should be able to disable the HTTP collector, so that associated assets 404
 * instead of allowing creation of spans.
 */
@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server", "zipkin.collector.http.enabled=false"]
)
@RunWith(SpringRunner::class)
class ITZipkinServerHttpCollectorDisabled {
  @Autowired lateinit var server: Server

  @Test fun httpCollectorEndpointReturns404() {
    val response = Http.post(server, "/api/v2/spans", body = "[]")

    assertThat(response.code()).isEqualTo(404)
  }

  /** Shows the same http path still works for GET  */
  @Test fun getOnSpansEndpointReturnsOK() {
    val response = Http.get(server, "/api/v2/spans?serviceName=unknown")

    assertThat(response.isSuccessful).isTrue()
  }
}
