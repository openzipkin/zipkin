/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.ui;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.io.ByteArrayInputStream;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ZipkinUiConfigurationTest {

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void indexContentType() {
    context = createContext();
    assertThat(
      serveIndex().headers().contentType())
      .isEqualTo(MediaType.HTML_UTF_8);
  }

  @Test
  public void indexHtml() throws Exception {
    // Instantiate directly so that spring doesn't cache it
    ZipkinUiConfiguration ui = new ZipkinUiConfiguration();
    ui.ui = new ZipkinUiProperties();
    ui.lensIndexHtml = new ClassPathResource("does-not-exist.html");
    assertThatThrownBy(ui::indexService)
      .isInstanceOf(BeanCreationException.class);
  }

  @Test
  public void canOverridesProperty_defaultLookback() {
    context = createContextWithOverridenProperty("zipkin.ui.defaultLookback:100");

    assertThat(context.getBean(ZipkinUiProperties.class).getDefaultLookback())
      .isEqualTo(100);
  }

  @Test
  public void canOverrideProperty_logsUrl() {
    final String url = "http://mycompany.com/kibana";
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isEqualTo(url);
  }

  @Test
  public void canOverrideProperty_archivePostUrl() {
    final String url = "http://zipkin.archive.com/api/v2/spans";
    context = createContextWithOverridenProperty("zipkin.ui.archive-post-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getArchivePostUrl()).isEqualTo(url);
  }

  @Test
  public void canOverrideProperty_archiveUrl() {
    final String url = "http://zipkin.archive.com/zipkin/traces/{traceId}";
    context = createContextWithOverridenProperty("zipkin.ui.archive-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getArchiveUrl()).isEqualTo(url);
  }

  @Test
  public void canOverrideProperty_supportUrl() {
    final String url = "http://mycompany.com/file-a-bug";
    context = createContextWithOverridenProperty("zipkin.ui.support-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getSupportUrl()).isEqualTo(url);
  }

  @Test
  public void logsUrlIsNullIfOverridenByEmpty() {
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:");

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  @Test
  public void logsUrlIsNullByDefault() {
    context = createContext();

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void canOverridesProperty_disable() {
    context = createContextWithOverridenProperty("zipkin.ui.enabled:false");

    context.getBean(ZipkinUiProperties.class);
  }

  @Test
  public void canOverridesProperty_searchEnabled() {
    context = createContextWithOverridenProperty("zipkin.ui.search-enabled:false");

    assertThat(context.getBean(ZipkinUiProperties.class).isSearchEnabled()).isFalse();
  }

  @Test
  public void canOverridesProperty_dependenciesEnabled() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.enabled:false");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().isEnabled()).isFalse();
  }

  @Test
  public void canOverrideProperty_dependencyLowErrorRate() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.low-error-rate:0.1");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().getLowErrorRate())
      .isEqualTo(0.1f);
  }

  @Test
  public void canOverrideProperty_dependencyHighErrorRate() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.high-error-rate:0.1");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().getHighErrorRate())
      .isEqualTo(0.1f);
  }

  @Test
  public void defaultBaseUrl_doesNotChangeResource() {
    context = createContext();

    assertThat(new ByteArrayInputStream(serveIndex().content().array()))
      .hasSameContentAs(getClass().getResourceAsStream("/zipkin-lens/index.html"));
  }

  @Test
  public void canOverrideProperty_basePath() {
    context = createContextWithOverridenProperty("zipkin.ui.basepath:/foo/bar");

    assertThat(serveIndex().contentUtf8())
      .contains("<base href=\"/foo/bar/\">");
  }

  @Test
  public void lensCookieOverridesIndex() {
    context = createContext();

    assertThat(serveIndex(new DefaultCookie("lens", "true")).contentUtf8())
      .contains("zipkin-lens");
  }

  @Test
  public void canOverrideProperty_specialCaseRoot() {
    context = createContextWithOverridenProperty("zipkin.ui.basepath:/");

    assertThat(serveIndex().contentUtf8())
      .contains("<base href=\"/\">");
  }

  AggregatedHttpResponse serveIndex(Cookie... cookies) {
    RequestHeaders headers = RequestHeaders.of(HttpMethod.GET, "/");
    String encodedCookies = ClientCookieEncoder.LAX.encode(cookies);
    if (encodedCookies != null) {
      headers = headers.toBuilder().set(HttpHeaderNames.COOKIE, encodedCookies).build();
    }
    HttpRequest req = HttpRequest.of(headers);
    try {
      return context.getBean(HttpService.class)
        .serve(ServiceRequestContext.of(req), req).aggregate()
        .get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static AnnotationConfigApplicationContext createContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinUiConfiguration.class);
    context.refresh();
    return context;
  }

  private static AnnotationConfigApplicationContext createContextWithOverridenProperty(
    String pair) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(pair).applyTo(context);
    context.register(PropertyPlaceholderAutoConfiguration.class, ZipkinUiConfiguration.class);
    context.refresh();
    return context;
  }
}
