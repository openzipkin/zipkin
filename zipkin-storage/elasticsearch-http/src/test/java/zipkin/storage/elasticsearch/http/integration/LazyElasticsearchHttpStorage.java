/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch.http.integration;

import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.HttpWaitStrategy;
import zipkin.internal.LazyCloseable;
import zipkin.storage.elasticsearch.http.ElasticsearchHttpStorage;
import zipkin.storage.elasticsearch.http.InternalForTests;
import zipkin2.CheckResult;

class LazyElasticsearchHttpStorage extends LazyCloseable<ElasticsearchHttpStorage>
  implements TestRule {
  static final String INDEX = "test_zipkin_http";

  final String image;
  GenericContainer container;

  LazyElasticsearchHttpStorage(String image) {
    this.image = image;
  }

  @Override protected ElasticsearchHttpStorage compute() {
    try {
      container = new GenericContainer(image)
        .withExposedPorts(9200)
        .waitingFor(new HttpWaitStrategy().forPath("/"));
      container.start();
      if (Boolean.valueOf(System.getenv("ES_DEBUG"))) {
        container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(image)));
      }
      System.out.println("Starting docker image " + image);
    } catch (RuntimeException e) {
      // Ignore
    }

    ElasticsearchHttpStorage result = computeStorageBuilder().build();
    CheckResult check = result.check();
    if (check.ok()) {
      return result;
    } else {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
  }

  ElasticsearchHttpStorage.Builder computeStorageBuilder() {
    OkHttpClient ok = Boolean.valueOf(System.getenv("ES_DEBUG"))
      ? new OkHttpClient.Builder()
      .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
      .addNetworkInterceptor(chain -> chain.proceed( // logging interceptor doesn't gunzip
        chain.request().newBuilder().removeHeader("Accept-Encoding").build()))
      .build()
      : new OkHttpClient();
    ElasticsearchHttpStorage.Builder builder = ElasticsearchHttpStorage.builder(ok).index(INDEX);
    InternalForTests.flushOnWrites(builder);
    return builder.hosts(Arrays.asList(baseUrl()));
  }

  String baseUrl() {
    if (container != null && container.isRunning()) {
      return String.format("http://%s:%d",
        container.getContainerIpAddress(),
        container.getMappedPort(9200)
      );
    } else {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
      return "http://localhost:9200";
    }
  }

  @Override public void close() {
    try {
      ElasticsearchHttpStorage storage = maybeNull();
      if (storage != null) storage.close();
    } finally {
      if (container != null) {
        System.out.println("Stopping docker image " + image);
        container.stop();
      }
    }
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        get();
        try {
          base.evaluate();
        } finally {
          close();
        }
      }
    };
  }
}
