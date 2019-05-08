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
import com.squareup.wire.GrpcClient
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.Span
import zipkin2.TestObjects.TRACE
import zipkin2.codec.SpanBytesDecoder
import zipkin2.codec.SpanBytesEncoder
import zipkin2.proto3.ListOfSpans
import zipkin2.proto3.ReportResponse
import zipkin2.storage.InMemoryStorage

/** This tests that we accept messages constructed by other clients. */
@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server", "zipkin.collector.grpc.enabled=true"]
)
@RunWith(SpringRunner::class)
// Written in Kotlin as Square Wire's grpc client is Kotlin.
// Also, the first author of this test wanted an excuse to write Kotlin.
class ITZipkinGrpcCollector {
  @Autowired lateinit var server: Server
  @Autowired lateinit var storage: InMemoryStorage
  @Before fun clearStorage() = storage.clear()

  val request: ListOfSpans = ListOfSpans.ADAPTER.decode(SpanBytesEncoder.PROTO3.encodeList(TRACE))
  lateinit var spanService: SpanService

  interface SpanService : Service {
    @WireRpc(
      path = "/zipkin.proto3.SpanService/Report",
      requestAdapter = "zipkin2.proto3.ListOfSpans#ADAPTER",
      responseAdapter = "zipkin2.proto3.ReportResponse#ADAPTER"
    )
    suspend fun Report(request: ListOfSpans): ReportResponse
  }

  @Before fun sanityCheckCodecCompatible() {
    assertThat(SpanBytesDecoder.PROTO3.decodeList(request.encode()))
      .containsExactlyElementsOf(TRACE)
  }

  @Before fun createClient() {
    spanService = GrpcClient.Builder()
      .client(OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build())
      .baseUrl("http://localhost:" + server.activePort().get().localAddress().port)
      .build().create(SpanService::class)
  }

  @Test fun report_trace() {
    runBlocking {
      spanService.Report(request) // Result is effectively void
    }
    assertThat<List<Span>>(storage.traces)
      .containsExactly(TRACE)
  }

  @Test fun report_emptyIsOk() {
    runBlocking {
      spanService.Report(ListOfSpans.Builder().build())
    }
  }
}
