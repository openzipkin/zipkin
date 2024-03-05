/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.elasticsearch.TestResponses.VERSION_RESPONSE;
import static zipkin2.server.internal.elasticsearch.TestResponses.YELLOW_RESPONSE;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.Ssl;

class ITElasticsearchAuth {

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension() {
    @Override protected void configureServer(ServerBuilder sb) throws Exception {
      sb.https(0);
      Ssl ssl = new Ssl();
      ssl.setKeyStore("classpath:keystore.jks");
      ssl.setKeyStorePassword("password");
      ssl.setTrustStore("classpath:keystore.jks");
      ssl.setTrustStorePassword("password");

      final KeyManagerFactory keyManagerFactory = SslUtil.getKeyManagerFactory(ssl);
      final TrustManagerFactory trustManagerFactory = SslUtil.getTrustManagerFactory(ssl);
      sb.tls(keyManagerFactory)
        .tlsCustomizer(sslContextBuilder -> {
          sslContextBuilder.keyManager(keyManagerFactory);
          sslContextBuilder.trustManager(trustManagerFactory);
        });
    }
  };

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  ElasticsearchStorage storage;

  @BeforeEach void init() {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type=elasticsearch",
      "zipkin.storage.elasticsearch.ensure-templates=false",
      "zipkin.storage.elasticsearch.username=Aladdin",
      "zipkin.storage.elasticsearch.password=OpenSesame",
      "zipkin.storage.elasticsearch.hosts=https://localhost:" + server.httpsPort(),
      "zipkin.storage.elasticsearch.ssl.key-store=classpath:keystore.jks",
      "zipkin.storage.elasticsearch.ssl.key-store-password=password",
      "zipkin.storage.elasticsearch.ssl.trust-store=classpath:keystore.jks",
      "zipkin.storage.elasticsearch.ssl.trust-store-password=password")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
    storage = context.getBean(ElasticsearchStorage.class);
  }

  @AfterEach void close() {
    storage.close();
  }

  @Test void healthcheck_usesAuthAndTls() {
    server.enqueue(VERSION_RESPONSE.toHttpResponse());
    server.enqueue(YELLOW_RESPONSE.toHttpResponse());

    assertThat(storage.check().ok()).isTrue();

    AggregatedHttpRequest next = server.takeRequest().request();
    // hard coded for sanity taken from https://en.wikipedia.org/wiki/Basic_access_authentication
    assertThat(next.headers().get("Authorization"))
      .isEqualTo("Basic QWxhZGRpbjpPcGVuU2VzYW1l");
  }
}
