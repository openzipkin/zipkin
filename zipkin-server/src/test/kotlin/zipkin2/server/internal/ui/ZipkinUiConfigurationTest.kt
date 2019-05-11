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

import com.linecorp.armeria.common.AggregatedHttpMessage
import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.common.RequestHeaders
import com.linecorp.armeria.server.ServiceRequestContext
import io.netty.handler.codec.http.cookie.ClientCookieEncoder
import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.io.ClassPathResource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

class ZipkinUiConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun indexHtmlFromClasspath() {
    refreshContext();

    assertThat(context.getBean(ZipkinUiConfiguration::class.java).indexHtml)
      .isNotNull
  }

  @Test fun indexContentType() {
    refreshContext()

    assertThat(serveIndex().headers().contentType())
      .isEqualTo(MediaType.HTML_UTF_8)
  }

  @Test fun invalidIndexHtml() {
    TestPropertyValues.of("zipkin.ui.basepath:/foo/bar").applyTo(context)
    refreshContext()

    // I failed to make Jsoup barf, even on nonsense like: "<head wait no I changed my mind this HTML is totally invalid <<<<<<<<<<<"
    // So let's just run with a case where the file doesn't exist
    val ui = context.getBean(ZipkinUiConfiguration::class.java)
    ui.indexHtml = ClassPathResource("does-not-exist.html")

    try {
      serveIndex()

      assertThat(false).isTrue()
    } catch (e: RuntimeException) {
      assertThat(e).hasRootCauseInstanceOf(FileNotFoundException::class.java)
    }
  }

  @Test fun canOverridesProperty_defaultLookback() {
    TestPropertyValues.of("zipkin.ui.defaultLookback:100").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).defaultLookback)
      .isEqualTo(100)
  }

  @Test fun canOverrideProperty_logsUrl() {
    val url = "http://mycompany.com/kibana"
    TestPropertyValues.of("zipkin.ui.logs-url:$url").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).logsUrl).isEqualTo(url)
  }

  @Test fun logsUrlIsNullIfOverridenByEmpty() {
    TestPropertyValues.of("zipkin.ui.logs-url:").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).logsUrl).isNull()
  }

  @Test fun logsUrlIsNullByDefault() {
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).logsUrl).isNull()
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun canOverridesProperty_disable() {
    TestPropertyValues.of("zipkin.ui.enabled:false").applyTo(context)
    refreshContext()

    context.getBean(ZipkinUiProperties::class.java)
  }

  @Test fun canOverridesProperty_searchEnabled() {
    TestPropertyValues.of("zipkin.ui.search-enabled:false").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).isSearchEnabled).isFalse()
  }

  @Test fun canOverrideProperty_dependencyLowErrorRate() {
    TestPropertyValues.of("zipkin.ui.dependency.low-error-rate:0.1").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).dependency.lowErrorRate)
      .isEqualTo(0.1f)
  }

  @Test fun canOverrideProperty_dependencyHighErrorRate() {
    TestPropertyValues.of("zipkin.ui.dependency.high-error-rate:0.1").applyTo(context)
    refreshContext()

    assertThat(context.getBean(ZipkinUiProperties::class.java).dependency.highErrorRate)
      .isEqualTo(0.1f)
  }

  @Test fun defaultBaseUrl_doesNotChangeResource() {
    refreshContext()

    assertThat(ByteArrayInputStream(serveIndex().content().array()))
      .hasSameContentAs(javaClass.getResourceAsStream("/zipkin-ui/index.html"))
  }

  @Test fun canOverideProperty_basePath() {
    TestPropertyValues.of("zipkin.ui.basepath:/foo/bar").applyTo(context)
    refreshContext()

    assertThat(serveIndex().contentUtf8())
      .contains("<base href=\"/foo/bar/\">")
  }

  @Test fun lensCookieOverridesIndex() {
    refreshContext()

    assertThat(serveIndex(DefaultCookie("lens", "true")).contentUtf8())
      .contains("zipkin-lens")
  }

  @Test fun canOverideProperty_specialCaseRoot() {
    TestPropertyValues.of("zipkin.ui.basepath:/").applyTo(context)
    refreshContext()

    assertThat(serveIndex().contentUtf8())
      .contains("<base href=\"/\">")
  }

  private fun serveIndex(vararg cookies: Cookie): AggregatedHttpMessage {
    val headers = RequestHeaders.builder(HttpMethod.GET, "/")
    val encodedCookies = ClientCookieEncoder.LAX.encode(*cookies)
    if (encodedCookies != null) {
      headers.set(HttpHeaderNames.COOKIE, encodedCookies)
    }
    val req = HttpRequest.of(headers.build())
    return context.getBean(ZipkinUiConfiguration::class.java).indexSwitchingService()
      .serve(ServiceRequestContext.of(req), req).aggregate()
      .get()
  }

  private fun refreshContext() {
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinUiConfiguration::class.java)
    context.refresh()
  }
}
