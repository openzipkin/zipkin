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
package zipkin2.server.internal.elasticsearch

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class BasicAuthInterceptorTest {
  @Rule @JvmField val mockWebServer = MockWebServer()
  @Rule @JvmField val thrown: ExpectedException = ExpectedException.none()

  var client: OkHttpClient = OkHttpClient.Builder()
    .addNetworkInterceptor(BasicAuthInterceptor(ZipkinElasticsearchStorageProperties(false, 0)))
    .build()

  @Test fun intercept_whenESReturns403AndJsonBody_throwsWithResponseBodyMessage() {
    thrown.expect(IllegalStateException::class.java)
    thrown.expectMessage("Sadness.")

    mockWebServer.enqueue(
      MockResponse().setResponseCode(403).setBody("{\"message\":\"Sadness.\"}"))

    client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()
  }
}
