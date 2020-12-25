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
package zipkin2.elasticsearch.integration;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.WebClientBuilder;
import com.linecorp.armeria.client.logging.ContentPreviewingClient;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.client.logging.LoggingClientBuilder;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.logging.LogLevel;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.ElasticsearchStorage.Builder;

import static org.testcontainers.utility.DockerImageName.parse;
import static zipkin2.elasticsearch.integration.IgnoredDeprecationWarnings.IGNORE_THESE_WARNINGS;

class ElasticsearchExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchExtension.class);

  final ElasticsearchContainer container;

  ElasticsearchExtension(int majorVersion) {
    container = new ElasticsearchContainer(majorVersion);
  }

  @Override public void beforeAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using baseUrl " + baseUrl());
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  Builder computeStorageBuilder() {
    WebClientBuilder builder = WebClient.builder(baseUrl())
      // Elasticsearch 7 never returns a response when receiving an HTTP/2 preface instead of the
      // more valid behavior of returning a bad request response, so we can't use the preface.
      //
      // TODO: find or raise a bug with Elastic
      .factory(ClientFactory.builder().useHttp2Preface(false).build());
    builder.decorator((delegate, ctx, req) -> {
      final HttpResponse response = delegate.execute(ctx, req);
      return HttpResponse.from(response.aggregate().thenApply(r -> {
        // ES will return a 'warning' response header when using deprecated api, detect this and
        // fail early so we can do something about it.
        // Example usage: https://github.com/elastic/elasticsearch/blob/3049e55f093487bb582a7e49ad624961415ba31c/x-pack/plugin/security/src/internalClusterTest/java/org/elasticsearch/integration/IndexPrivilegeIntegTests.java#L559
        final String warningHeader = r.headers().get("warning");
        if (warningHeader != null) {
          if (IGNORE_THESE_WARNINGS.stream().noneMatch(p -> p.matcher(warningHeader).find())) {
            throw new IllegalArgumentException("Detected usage of deprecated API for request "
              + req.toString() + ":\n" + warningHeader);
          }
        }
        // Convert AggregatedHttpResponse back to HttpResponse.
        return r.toHttpResponse();
      }));
    });

    // When ES_DEBUG=true log full headers, request and response body to the category
    // com.linecorp.armeria.client.logging
    if (Boolean.parseBoolean(System.getenv("ES_DEBUG"))) {
      ClientOptionsBuilder options = ClientOptions.builder();
      LoggingClientBuilder loggingBuilder = LoggingClient.builder()
        .requestLogLevel(LogLevel.INFO)
        .successfulResponseLogLevel(LogLevel.INFO);
      options.decorator(loggingBuilder.newDecorator());
      options.decorator(ContentPreviewingClient.newDecorator(Integer.MAX_VALUE));
      builder.options(options.build());
    }

    WebClient client = builder.build();
    return ElasticsearchStorage.newBuilder(new ElasticsearchStorage.LazyHttpClient() {
      @Override public WebClient get() {
        return client;
      }

      @Override public void close() {
        client.endpointGroup().close();
      }

      @Override public String toString() {
        return client.uri().toString();
      }
    }).index("zipkin-test").flushOnWrites(true);
  }

  String baseUrl() {
    return "http://" + container.getHost() + ":" + container.getMappedPort(9200);
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class ElasticsearchContainer extends GenericContainer<ElasticsearchContainer> {
    ElasticsearchContainer(int majorVersion) {
      super(parse("ghcr.io/openzipkin/zipkin-elasticsearch" + majorVersion + ":2.23.2"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      waitStrategy = Wait.forHealthcheck();
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }

  static String index(TestInfo testInfo) {
    String result;
    if (testInfo.getTestMethod().isPresent()) {
      result = testInfo.getTestMethod().get().getName();
    } else {
      assert testInfo.getTestClass().isPresent();
      result = testInfo.getTestClass().get().getSimpleName();
    }
    result = result.toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
