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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit.server.mock.MockWebServerExtension;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static zipkin2.elasticsearch.Access.pretendIndexTemplatesExist;
import static zipkin2.server.internal.elasticsearch.TestResponses.YELLOW_RESPONSE;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.PASSWORD_PROP;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration.USERNAME_PROP;
import static zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.Ssl;

class ITElasticsearchDynamicCredentials {

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
  File credentialsFile;

  @BeforeEach void init() throws IOException {
    credentialsFile = File.createTempFile("zipkin-server", "credentials");
    Properties props = new Properties();
    props.put(USERNAME_PROP, "foo");
    props.put(PASSWORD_PROP, "bar");
    try (FileOutputStream os = new FileOutputStream(credentialsFile)) {
      props.store(os, "");
    }
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:https://localhost:" + server.httpsPort(),
      "zipkin.storage.elasticsearch.credentials-file=" + credentialsFile.getAbsolutePath(),
      "zipkin.storage.elasticsearch.credentials-refresh-interval=3",
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

  @Test void healthcheck_usesDynamicCredentialsAndTls() throws IOException {
    pretendIndexTemplatesExist(storage);

    assertTimeout(ofSeconds(30), () -> {
      while (true) {
        server.enqueue(YELLOW_RESPONSE.toHttpResponse());
        assertThat(storage.check().ok()).isTrue();
        AggregatedHttpRequest next = server.takeRequest().request();
        // hard coded for sanity taken from https://en.wikipedia.org/wiki/Basic_access_authentication
        if (next.headers().get("Authorization").equals("Basic Zm9vOmJhcg==")) {
          break;
        }
      }
    });

    // Update security file with new username/password
    Properties props = new Properties();
    props.put(USERNAME_PROP, "foo1");
    props.put(PASSWORD_PROP, "bar1");
    try (FileOutputStream os = new FileOutputStream(credentialsFile)) {
      props.store(os, "");
    }
    assertTimeout(ofSeconds(30), () -> {
      while (true) {
        server.enqueue(YELLOW_RESPONSE.toHttpResponse());
        assertThat(storage.check().ok()).isTrue();
        AggregatedHttpRequest next = server.takeRequest().request();
        // hard coded for sanity taken from https://en.wikipedia.org/wiki/Basic_access_authentication
        if (next.headers().get("Authorization").equals("Basic Zm9vMTpiYXIx")) {
          break;
        }
      }
    });
  }
}
