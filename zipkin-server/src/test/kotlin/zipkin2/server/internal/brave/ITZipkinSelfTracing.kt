/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal.brave

import com.linecorp.armeria.server.Server
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.After
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
  properties = [
    "spring.config.name=zipkin-server",
    "zipkin.self-tracing.enabled=true",
    "zipkin.self-tracing.message-timeout=1ms",
    "zipkin.self-tracing.traces-per-second=0"
  ]
)
@RunWith(SpringRunner::class)
class ITZipkinSelfTracing {
  @Autowired lateinit var server: Server
  @Autowired lateinit var storage: TracingStorageComponent

  lateinit var inMemoryStorage: InMemoryStorage

  @Before fun setUp() {
    inMemoryStorage = (storage.delegate as InMemoryStorage)
  }

  @After fun clearStorage() {
    inMemoryStorage.clear()
  }

  @Test fun getIsTraced_v2() {
    assertThat(Http.getAsString(server, "/api/v2/services"))
      .isEqualTo("[]")

    assertServerTraced()

    val traces = inMemoryStorage.traces
    assertThat(traces).hasSize(1)
    assertThat(traces[0]).anyMatch {
      it.name() == "get" && it.tags()["http.path"] == "/api/v2/services"
    }
  }

  @Test fun staticNotTraced() {
    // Get a couple of never-changing static resource URLs
    assertThat(Http.getAsString(server, "/zipkin/")).contains("<html>")
    assertThat(Http.get(server, "/zipkin/favicon.ico").isSuccessful).isTrue()

    // Can just run another test as the above request should not affect its expectations.
    getIsTraced_v2()
  }

  @Test fun postIsTraced_v1() {
    assertThat(Http.post(server, "/api/v1/spans", body = "[]").isSuccessful).isTrue()

    assertServerTraced()

    val traces = inMemoryStorage.traces
    assertThat(traces).hasSize(1)
    assertThat(traces[0]).anyMatch {
      it.name() == "post" && it.tags()["http.path"] == "/api/v1/spans"
    }
  }

  @Test fun postIsTraced_v2() {
    assertThat(Http.post(server, "/api/v2/spans", body = "[]").isSuccessful).isTrue()

    assertServerTraced()

    val traces = inMemoryStorage.traces
    assertThat(traces).hasSize(1)
    assertThat(traces[0]).anyMatch {
      it.name() == "post" && it.tags()["http.path"] == "/api/v2/spans"
    }
  }

  private fun assertServerTraced() {
    // wait for spans
    await().untilAsserted {
      assertThat(inMemoryStorage.acceptedSpanCount()).isGreaterThanOrEqualTo(1)
    }

    assertThat(inMemoryStorage.serviceNames.execute()).containsExactly("zipkin-server")
  }
}
