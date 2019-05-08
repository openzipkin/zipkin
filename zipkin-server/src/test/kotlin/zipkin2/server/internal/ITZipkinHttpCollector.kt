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
import okhttp3.Response
import okio.Buffer
import okio.GzipSink
import org.assertj.core.api.Assert
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import zipkin.server.ZipkinServer
import zipkin2.TestObjects.TRACE
import zipkin2.codec.SpanBytesEncoder
import zipkin2.storage.InMemoryStorage

@SpringBootTest(
  classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server"]
)
@RunWith(SpringRunner::class)
class ITZipkinHttpCollector {
  @Autowired lateinit var server: Server
  @Autowired lateinit var storage: InMemoryStorage
  @Before fun clearStorage() = storage.clear()

  @Test fun noContentTypeIsJsonV2() {
    val response = Http.post(server, "/api/v2/spans", null,
      SpanBytesEncoder.JSON_V2.encodeList(TRACE)
    )

    assertThat(response.code())
      .isEqualTo(202)

    assertThat(storage.traces).containsExactly(TRACE)
  }

  @Test fun jsonV2() {
    val response = Http.post(server, "/api/v2/spans", "application/json",
      SpanBytesEncoder.JSON_V2.encodeList(TRACE)
    )

    assertThat(response.code())
      .isEqualTo(202)

    assertThat(storage.traces).containsExactly(TRACE)
  }

  @Test fun jsonV2_accidentallySentV1Format() {
    val response = Http.post(server, "/api/v2/spans", "application/json",
      SpanBytesEncoder.JSON_V1.encodeList(TRACE)
    )

    assertThat(response.code()).isEqualTo(400)
    assertThat(response.body()!!.string())
      .startsWith("Expected a JSON_V2 encoded list, but received: JSON_V1\n")
  }

  @Test fun jsonV1_accidentallySentV2Format() {
    val response = Http.post(server, "/api/v1/spans", "application/json",
      SpanBytesEncoder.JSON_V2.encodeList(TRACE)
    )

    assertThat(response.code()).isEqualTo(400)
    assertThat(response.body()!!.string())
      .startsWith("Expected a JSON_V1 encoded list, but received: JSON_V2\n")
  }

  @Test fun ambiguousFormatOk() {
    val body = """[{"traceId": "1", "id": "1", "name": "test"}]"""

    assertThat(Http.post(server, "/api/v1/spans", null, body).code()).isEqualTo(202)
    assertThat(Http.post(server, "/api/v2/spans", null, body).code()).isEqualTo(202)
  }

  @Test fun emptyIsOk() {
    assertOnAllEndpoints(byteArrayOf()) { response: Response,
      path: String, contentType: String, encoding: String ->
      assertThat(response.isSuccessful)
        .withFailMessage("$path $contentType $encoding failed")
        .isTrue()
    }
  }

  @Test fun malformedNotOk() {
    assertOnAllEndpoints(byteArrayOf(1, 2, 3, 4)) { response: Response,
      path: String, contentType: String, encoding: String ->
      assertThat(response.code()).isEqualTo(400)

      if (encoding == "identity") {
        assertThat(response.body()!!.string())
          .withFailMessage("$path $contentType $encoding failed")
          .contains("Expected a ", " encoded list\n")
      } else {
        assertThat(response.body()!!.string())
          .withFailMessage("$path $contentType $encoding failed")
          .contains("Cannot gunzip spans")
      }
    }
  }

  fun assertOnAllEndpoints(
    body: ByteArray,
    assertion: (Response, String, String, String) -> Assert<*, *>
  ) {
    listOf(
      Pair("/api/v2/spans", "application/json"),
      Pair("/api/v2/spans", "application/x-protobuf"),
      Pair("/api/v1/spans", "application/json"),
      Pair("/api/v1/spans", "application/x-thrift")
    ).forEach {
      val (path, contentType) = it
      for (encoding in listOf("identity", "gzip")) {
        val response =
          Http.post(server, path, contentType, body, Headers.of("Content-Encoding", encoding))

        assertion(response, path, contentType, encoding)
      }
    }
  }

  @Test fun contentTypeXThrift() {
    val response = Http.post(server, "/api/v1/spans", "application/x-thrift",
      SpanBytesEncoder.THRIFT.encodeList(TRACE)
    )

    assertThat(response.code())
      .isEqualTo(202)
  }

  @Test fun contentTypeXProtobuf() {
    val response = Http.post(server, "/api/v2/spans", "application/x-protobuf",
      SpanBytesEncoder.PROTO3.encodeList(TRACE)
    )

    assertThat(response.code())
      .isEqualTo(202)
  }

  @Test fun gzipEncoded() {
    val response = Http.post(server, "/api/v2/spans", null,
      gzip(SpanBytesEncoder.JSON_V2.encodeList(TRACE)),
      Headers.of("Content-Encoding", "gzip")
    )

    assertThat(response.code())
      .isEqualTo(202)
  }

  fun gzip(message: ByteArray): ByteArray {
    val sink = Buffer()
    val gzipSink = GzipSink(sink)
    gzipSink.write(Buffer().write(message), message.size.toLong())
    gzipSink.close()
    return sink.readByteArray()
  }
}
