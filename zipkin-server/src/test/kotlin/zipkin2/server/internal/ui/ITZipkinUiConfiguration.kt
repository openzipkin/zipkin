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
package zipkin2.server.internal.ui

import com.linecorp.armeria.client.HttpClient
import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.RequestHeaders
import com.linecorp.armeria.server.Server
import okhttp3.Headers
import okio.Okio
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit4.SpringRunner
import zipkin2.server.internal.Http

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [ITZipkinUiConfiguration.TestServer::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "zipkin.ui.base-path=/foozipkin",
    "server.compression.enabled=true",
    "server.compression.min-response-size=128"
  ]
)
class ITZipkinUiConfiguration {
  @Autowired lateinit var server: Server

  /** The zipkin-ui is a single-page app. This prevents reloading all resources on each click.  */
  @Test fun setsMaxAgeOnUiResources() {
    assertThat(Http.get(server, "/zipkin/config.json").header("Cache-Control"))
      .isEqualTo("max-age=600")
    assertThat(Http.get(server, "/zipkin/index.html").header("Cache-Control"))
      .isEqualTo("max-age=60")
    assertThat(Http.get(server, "/zipkin/test.txt").header("Cache-Control"))
      .isEqualTo("max-age=31536000")
  }

  @Test fun redirectsIndex() {
    val index = Http.getAsString(server, "/zipkin/index.html")
    assertThat(Http.getAsString(server, "/zipkin/")).isEqualTo(index)

    listOf("/zipkin", "/").forEach { path ->
      val response = Http.get(server, path)
      assertThat(response.code()).isEqualTo(302)
      assertThat(response.header("location")).isEqualTo("/zipkin/")
    }
  }

  /** Browsers honor conditional requests such as eTag. Let's make sure the server does  */
  @Test fun conditionalRequests() {
    listOf("/zipkin/config.json", "/zipkin/index.html", "/zipkin/test.txt").forEach { path ->
      val etag = Http.get(server, path).header("etag")
      assertThat(Http.get(server, path, Headers.of("If-None-Match", etag)).code())
        .isEqualTo(304)
      assertThat(Http.get(server, path, Headers.of("If-None-Match", "aargh")).code())
        .isEqualTo(200)
    }
  }

  /** Some assets are pretty big. ensure they use compression.  */
  @Test fun supportsCompression() {
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/test.txt"))
      .isNull() // too small to compress
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/config.json"))
      .isEqualTo("gzip")
  }

  /**
   * The test sets the property `zipkin.ui.base-path=/foozipkin`, which should reflect in
   * index.html
   */
  @Test fun replacesBaseTag() {
    assertThat(Http.getAsString(server, "/zipkin/index.html"))
      .isEqualToIgnoringWhitespace(stringFromClasspath("zipkin-ui/index.html")
        .replace("<base href=\"/\" />", "<base href=\"/foozipkin/\">"))
  }

  /** index.html is served separately. This tests other content is also loaded from the classpath.  */
  @Test fun servesOtherContentFromClasspath() {
    assertThat(Http.getAsString(server, "/zipkin/test.txt"))
      .isEqualToIgnoringWhitespace(stringFromClasspath("zipkin-ui/test.txt"))
  }

  @EnableAutoConfiguration
  @Import(ZipkinUiConfiguration::class)
  class TestServer

  private fun stringFromClasspath(path: String): String {
    val url = javaClass.classLoader.getResource(path)
    assertThat(url).isNotNull()

    url!!.openStream()
      .use { fromClasspath -> return Okio.buffer(Okio.source(fromClasspath)).readUtf8() }
  }

  private fun getContentEncodingFromRequestThatAcceptsGzip(path: String): String? {
    // We typically use OkHttp in our tests, but that automatically unzips..
    val request = RequestHeaders.builder(HttpMethod.GET, path)
      .set(HttpHeaderNames.ACCEPT_ENCODING, "gzip")
      .build()
    val response = HttpClient.of(Http.url(server, "")).execute(request).aggregate().join()
    return response.headers().get(HttpHeaderNames.CONTENT_ENCODING)
  }
}
