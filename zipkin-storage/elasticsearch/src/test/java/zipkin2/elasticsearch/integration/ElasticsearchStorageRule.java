/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import com.google.common.io.Closer;
import java.io.IOException;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.HttpWaitStrategy;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

public class ElasticsearchStorageRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchStorageRule.class);
  static final int ELASTICSEARCH_PORT = 9200;
  final String image;
  final String index;
  GenericContainer container;
  Closer closer = Closer.create();

  public ElasticsearchStorageRule(String image, String index) {
    this.image = image;
    this.index = index;
  }

  @Override
  protected void before() throws Throwable {
    try {
      LOGGER.info("Starting docker image " + image);
      container = new GenericContainer(image)
        .withExposedPorts(ELASTICSEARCH_PORT)
        .waitingFor(new HttpWaitStrategy().forPath("/"));
      container.start();
      if (Boolean.valueOf(System.getenv("ES_DEBUG"))) {
        container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(image)));
      }
      System.out.println("Starting docker image " + image);
    } catch (RuntimeException e) {
      LOGGER.warn("Couldn't start docker image " + image + ": " + e.getMessage(), e);
    }

    try {
      tryToInitializeSession();
    } catch (RuntimeException | Error e) {
      if (container == null) throw e;
      LOGGER.warn("Couldn't connect to docker image " + image + ": " + e.getMessage(), e);
      container.stop();
      container = null; // try with local connection instead
      tryToInitializeSession();
    }
  }

  void tryToInitializeSession() throws IOException {
    ElasticsearchStorage result = computeStorageBuilder().build();
    CheckResult check = result.check();
    if (!check.ok()) {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
  }

  public ElasticsearchStorage.Builder computeStorageBuilder() {
    OkHttpClient ok = Boolean.valueOf(System.getenv("ES_DEBUG"))
      ? new OkHttpClient.Builder()
      .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
      .addNetworkInterceptor(chain -> chain.proceed( // logging interceptor doesn't gunzip
        chain.request().newBuilder().removeHeader("Accept-Encoding").build()))
      .build()
      : new OkHttpClient();
    return ElasticsearchStorage.newBuilder(ok).index(index)
      .flushOnWrites(true)
      .hosts(Arrays.asList(baseUrl()));
  }

  String baseUrl() {
    if (container != null && container.isRunning()) {
      return String.format("http://%s:%d",
        container.getContainerIpAddress(),
        container.getMappedPort(ELASTICSEARCH_PORT)
      );
    } else {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
      return "http://localhost:" + ELASTICSEARCH_PORT;
    }
  }

  @Override protected void after() {
    try {
      closer.close();
    } catch (Exception | Error e) {
      LOGGER.warn("error closing session " + e.getMessage(), e);
    } finally {
      if (container != null) {
        LOGGER.info("Stopping docker image " + image);
        container.stop();
      }
    }
  }

  public static String index(TestName testName) {
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
