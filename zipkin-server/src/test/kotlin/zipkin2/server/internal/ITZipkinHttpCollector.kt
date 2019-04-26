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
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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
import zipkin2.Span
import zipkin2.TestObjects.TRACE
import zipkin2.codec.SpanBytesEncoder
import zipkin2.storage.InMemoryStorage
import java.io.IOException
import java.util.Arrays.asList

@SpringBootTest(classes = [ZipkinServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["spring.config.name=zipkin-server"])
@RunWith(SpringRunner::class)
class ITZipkinHttpCollector {
  @Autowired lateinit var storage: InMemoryStorage
  @Autowired lateinit var server: Server

  var client = OkHttpClient.Builder().followRedirects(true).build()

  @Before fun init() {
    storage.clear()
  }

  @Test @Throws(IOException::class)
  fun noContentTypeIsJsonV2() {
    val response = post("/api/v2/spans", SpanBytesEncoder.JSON_V2.encodeList(TRACE))

    assertThat(response.code())
      .isEqualTo(202)

    assertThat(storage.traces).containsExactly(TRACE);
  }

  @Test @Throws(IOException::class)
  fun jsonV2() {
    val body = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
    val response = client.newCall(Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .post(RequestBody.create(MediaType.parse("application/json"), body))
      .build()).execute()

    assertThat(response.code())
      .isEqualTo(202)

    assertThat(storage.traces).containsExactly(TRACE);
  }

  @Test @Throws(IOException::class)
  fun jsonV2_accidentallySentV1Format() {
    val message = SpanBytesEncoder.JSON_V1.encodeList(TRACE)

    val response = post("/api/v2/spans", message)
    assertThat(response.code()).isEqualTo(400)
    assertThat(response.body()!!.string())
      .startsWith("Expected a JSON_V2 encoded list, but received: JSON_V1\n")
  }

  @Test @Throws(IOException::class)
  fun jsonV1_accidentallySentV2Format() {
    val message = SpanBytesEncoder.JSON_V2.encodeList(TRACE)

    val response = post("/api/v1/spans", message)
    assertThat(response.code()).isEqualTo(400)
    assertThat(response.body()!!.string())
      .startsWith("Expected a JSON_V1 encoded list, but received: JSON_V2\n")
  }

  @Test @Throws(IOException::class)
  fun ambiguousFormatOk() {
    val message = SpanBytesEncoder.JSON_V2.encodeList(asList(
      Span.newBuilder().traceId("1").id("1").name("test").build()
    ))

    assertThat(post("/api/v1/spans", message).code()).isEqualTo(202)
    assertThat(post("/api/v2/spans", message).code()).isEqualTo(202)
  }

  @Test @Throws(IOException::class)
  fun emptyIsOk() {
    assertOnAllEndpoints(byteArrayOf()) { response: Response,
      path: String, contentType: String, encoding: String ->
      assertThat(response.isSuccessful)
        .withFailMessage("$path $contentType $encoding failed")
        .isTrue()
    }
  }

  @Test @Throws(IOException::class)
  fun malformedNotOk() {
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
        val response = client.newCall(Request.Builder()
          .url(url(server, path))
          .header("Content-Encoding", encoding)
          .post(RequestBody.create(MediaType.get(contentType), body))
          .build()).execute()

        assertion(response, path, contentType, encoding)
      }
    }
  }

  @Test @Throws(IOException::class)
  fun contentTypeXThrift() {
    val message = SpanBytesEncoder.THRIFT.encodeList(TRACE)

    val response = client.newCall(Request.Builder()
      .url(url(server, "/api/v1/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-thrift"), message))
      .build()).execute()

    assertThat(response.code())
      .withFailMessage(response.body()!!.string())
      .isEqualTo(202)
  }

  @Test @Throws(IOException::class)
  fun contentTypeXProtobuf() {
    val message = SpanBytesEncoder.PROTO3.encodeList(TRACE)

    val response = client.newCall(Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-protobuf"), message))
      .build()).execute()

    assertThat(response.code())
      .isEqualTo(202)
  }

  @Test @Throws(IOException::class)
  fun gzipEncoded() {
    val message = SpanBytesEncoder.JSON_V2.encodeList(TRACE)
    val gzippedBody = gzip(message)

    val response = client.newCall(Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .header("Content-Encoding", "gzip")
      .post(RequestBody.create(null, gzippedBody))
      .build()).execute()

    assertThat(response.isSuccessful)
  }

  fun gzip(message: ByteArray): ByteArray {
    val sink = Buffer()
    val gzipSink = GzipSink(sink)
    gzipSink.write(Buffer().write(message), message.size.toLong())
    gzipSink.close()
    val gzippedBody = sink.readByteArray()
    return gzippedBody
  }

  @Throws(IOException::class)
  fun get(path: String): Response {
    return client.newCall(Request.Builder()
      .url(url(server, path))
      .build()).execute()
  }

  @Throws(IOException::class)
  fun post(path: String, body: ByteArray): Response {
    return client.newCall(Request.Builder()
      .url(url(server, path))
      .post(RequestBody.create(null, body))
      .build()).execute()
  }

  fun url(server: Server, path: String): String {
    return "http://localhost:" + server.activePort().get().localAddress().port + path
  }
}
