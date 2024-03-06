/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinUiConfigurationTest {

  AnnotationConfigApplicationContext context;

  @AfterEach void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test void indexContentType() {
    context = createContext();
    assertThat(
      serveIndex().headers().contentType())
      .isEqualTo(MediaType.HTML_UTF_8);
  }

  @Test void indexHtml() {
    // Instantiate directly so that spring doesn't cache it
    ZipkinUiConfiguration ui = new ZipkinUiConfiguration();
    ui.ui = new ZipkinUiProperties();
    ui.lensIndexHtml = new ClassPathResource("does-not-exist.html");
    assertThatExceptionOfType(BeanCreationException.class).isThrownBy(ui::indexService);
  }

  @Test void canOverridesProperty_defaultLookback() {
    context = createContextWithOverridenProperty("zipkin.ui.defaultLookback:100");

    assertThat(context.getBean(ZipkinUiProperties.class).getDefaultLookback())
      .isEqualTo(100);
  }

  @Test void canOverrideProperty_logsUrl() {
    final String url = "http://mycompany.com/kibana";
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isEqualTo(url);
  }

  @Test void canOverrideProperty_archivePostUrl() {
    final String url = "http://zipkin.archive.com/api/v2/spans";
    context = createContextWithOverridenProperty("zipkin.ui.archive-post-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getArchivePostUrl()).isEqualTo(url);
  }

  @Test void canOverrideProperty_archiveUrl() {
    final String url = "http://zipkin.archive.com/zipkin/traces/{traceId}";
    context = createContextWithOverridenProperty("zipkin.ui.archive-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getArchiveUrl()).isEqualTo(url);
  }

  @Test void canOverrideProperty_supportUrl() {
    final String url = "http://mycompany.com/file-a-bug";
    context = createContextWithOverridenProperty("zipkin.ui.support-url:" + url);

    assertThat(context.getBean(ZipkinUiProperties.class).getSupportUrl()).isEqualTo(url);
  }

  @Test void logsUrlIsNullIfOverridenByEmpty() {
    context = createContextWithOverridenProperty("zipkin.ui.logs-url:");

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  @Test void logsUrlIsNullByDefault() {
    context = createContext();

    assertThat(context.getBean(ZipkinUiProperties.class).getLogsUrl()).isNull();
  }

  @Test void canOverridesProperty_disable() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = createContextWithOverridenProperty("zipkin.ui.enabled:false");

      context.getBean(ZipkinUiProperties.class);
    });
  }

  @Test void canOverridesProperty_searchEnabled() {
    context = createContextWithOverridenProperty("zipkin.ui.search-enabled:false");

    assertThat(context.getBean(ZipkinUiProperties.class).isSearchEnabled()).isFalse();
  }

  @Test void canOverridesProperty_dependenciesEnabled() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.enabled:false");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().isEnabled()).isFalse();
  }

  @Test void canOverrideProperty_dependencyLowErrorRate() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.low-error-rate:0.1");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().getLowErrorRate())
      .isEqualTo(0.1f);
  }

  @Test void canOverrideProperty_dependencyHighErrorRate() {
    context = createContextWithOverridenProperty("zipkin.ui.dependency.high-error-rate:0.1");

    assertThat(context.getBean(ZipkinUiProperties.class).getDependency().getHighErrorRate())
      .isEqualTo(0.1f);
  }

  @Test void defaultBaseUrl_doesNotChangeResource() {
    context = createContext();

    assertThat(new ByteArrayInputStream(serveIndex().content().array()))
      .hasSameContentAs(getClass().getResourceAsStream("/zipkin-lens/index.html"));
  }

  @Test void canOverrideProperty_basePath() {
    context = createContextWithOverridenProperty("zipkin.ui.basepath:/admin/zipkin");

    assertThat(serveIndex().contentUtf8()).isEqualTo("""
      <!-- simplified version of /zipkin-lens/index.html -->
      <html>
        <head>
          <base href="/admin/zipkin/">
          <link rel="icon" href="./favicon.ico">
          <script type="module" crossorigin="" src="./static/js/index.js"></script>
          <link rel="stylesheet" href="./static/css/index.css">
        </head>
        <body>zipkin-lens</body>
      </html>
      """
    );
  }

  @Test void lensCookieOverridesIndex() {
    context = createContext();

    assertThat(serveIndex(new DefaultCookie("lens", "true")).contentUtf8())
      .contains("zipkin-lens");
  }

  @Test void canOverrideProperty_root() {
    context = createContextWithOverridenProperty("zipkin.ui.basepath:/");

    assertThat(serveIndex().contentUtf8()).isEqualTo("""
      <!-- simplified version of /zipkin-lens/index.html -->
      <html>
        <head>
          <base href="/">
          <link rel="icon" href="./favicon.ico">
          <script type="module" crossorigin="" src="./static/js/index.js"></script>
          <link rel="stylesheet" href="./static/css/index.css">
        </head>
        <body>zipkin-lens</body>
      </html>
      """
    );
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
