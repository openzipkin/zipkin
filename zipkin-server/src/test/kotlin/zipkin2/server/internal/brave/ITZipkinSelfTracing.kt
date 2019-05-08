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
package zipkin2.server.internal.brave

import com.linecorp.armeria.server.Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.server.internal.Http
import zipkin2.storage.InMemoryStorage

@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server", "zipkin.self-tracing.enabled=true"]
)
@RunWith(SpringRunner::class)
class ITZipkinSelfTracing {
  @Autowired lateinit var server: Server
  @Autowired lateinit var storage: TracingStorageComponent
  @Before fun clearStorage() = (storage.delegate as InMemoryStorage).clear()

  @Test fun getIsTraced_v2() {
    assertThat(Http.getAsString(server, "/api/v2/services"))
      .isEqualTo("[]")

    assertServerTraced()
  }

  @Test fun postIsTraced_v1() {
    assertThat(Http.post(server, "/api/v1/spans", body = "[]").isSuccessful).isTrue()

    assertServerTraced()
  }

  @Test fun postIsTraced_v2() {
    assertThat(Http.post(server, "/api/v2/spans", body = "[]").isSuccessful).isTrue()

    assertServerTraced()
  }

  private fun assertServerTraced() {
    Thread.sleep(1500) // wait for reporting interval

    assertThat(Http.getAsString(server, "/api/v2/services"))
      .isEqualTo("[\"zipkin-server\"]")
  }
}
