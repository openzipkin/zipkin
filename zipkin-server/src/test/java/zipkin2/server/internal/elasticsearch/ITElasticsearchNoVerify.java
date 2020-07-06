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

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
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

class ITElasticsearchNoVerify {
  @RegisterExtension
  static MockWebServerExtension server = new MockWebServerExtension();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  ElasticsearchStorage storage;

  @BeforeEach void init() {
    TestPropertyValues.of(
        "spring.config.name=zipkin-server",
        "zipkin.storage.type=elasticsearch",
        "zipkin.storage.elasticsearch.ensure-templates=false",
        "zipkin.storage.elasticsearch.hosts=https://localhost:" + server.httpsPort(),
        "zipkin.storage.elasticsearch.ssl.no-verify=true")
        .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
    storage = context.getBean(ElasticsearchStorage.class);
  }

  @AfterEach void close() {
    storage.close();
  }

  @Test void healthcheck_no_tls_verify() {
    server.enqueue(VERSION_RESPONSE.toHttpResponse());
    server.enqueue(YELLOW_RESPONSE.toHttpResponse());

    assertThat(storage.check().ok()).isTrue();
  }

  @Test void service_no_tls_verify() throws Exception {
    server.enqueue(
        AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.ofUtf8("{}")));

    assertThat(storage.serviceAndSpanNames().getServiceNames().execute()).isEmpty();
  }
}
