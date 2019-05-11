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

import com.linecorp.armeria.common.SessionProtocol
import com.linecorp.armeria.server.Server
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import zipkin2.TestObjects.UTF_8

object Http {
  var client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).build()

  fun get(server: Server, path: String, headers: Headers = Headers.of()): Response =
    client.newCall(Request.Builder().url(url(server, path)).headers(headers).build()).execute()

  fun getAsString(server: Server, path: String, headers: Headers = Headers.of()): String =
    get(server, path, headers).body()!!.string()

  fun post(
    server: Server,
    path: String,
    contentType: String? = "application/json",
    body: String,
    headers: Headers = Headers.of()
  ) = post(server, path, contentType, body.toByteArray(UTF_8), headers)

  fun post(
    server: Server,
    path: String,
    contentType: String? = "application/json",
    body: ByteArray,
    headers: Headers = Headers.of()
  ): Response {
    val requestBody =
      RequestBody.create(if (contentType != null) MediaType.parse(contentType) else null, body)
    val result = client.newCall(
      Request.Builder().url(url(server, path)).headers(headers).post(requestBody).build()).execute()
    return result;
  }

  fun url(server: Server, path: String, protocol: SessionProtocol = SessionProtocol.HTTP): String {
    return server.activePorts().values.stream()
      .filter { p -> p.hasProtocol(protocol) }.findAny()
      .map { p -> protocol.uriText() + "://127.0.0.1:" + p.localAddress().port + path }
      .orElseThrow { AssertionError("$protocol port not open") }
  }
}
