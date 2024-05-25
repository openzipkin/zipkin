/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.integration;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestHeaders;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.InternalForTests;
import zipkin2.storage.StorageComponent;

import static zipkin2.elasticsearch.integration.ElasticsearchExtension.index;
import static zipkin2.storage.ITDependencies.aggregateLinks;

abstract class ITElasticsearchStorage {

  static final Logger LOGGER = LoggerFactory.getLogger(ITElasticsearchStorage.class);

  abstract ElasticsearchBaseExtension elasticsearch();

  @Nested
  class ITTraces extends zipkin2.storage.ITTraces<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStoreHeavy extends zipkin2.storage.ITSpanStoreHeavy<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITDependencies extends zipkin2.storage.ITDependencies<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override protected void processDependencies(List<Span> spans) {
      aggregateDependencies(storage, spans);
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesHeavy extends zipkin2.storage.ITDependenciesHeavy<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }

    @Override protected void processDependencies(List<Span> spans) {
      aggregateDependencies(storage, spans);
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links, usually made via zipkin-dependencies
   */
  static void aggregateDependencies(ElasticsearchStorage storage, List<Span> spans) {
    aggregateLinks(spans).forEach(
      (midnight, links) -> InternalForTests.writeDependencyLinks(
        storage, links, midnight));
  }

  @Test void usageOfDeprecatedFeatures() {
    WebClient webClient = WebClient.builder(elasticsearch().baseUrl()).factory(ClientFactory.builder()
      .useHttp2Preface(false).build()).build();
    final AggregatedHttpResponse response =
      webClient.execute(HttpRequest.of(RequestHeaders.of(HttpMethod.GET,
        "/_migration/deprecations"))).aggregate().join();
    String responseBody = response.contentAscii();
    if (!responseBody.equals("""
      {"cluster_settings":[],"node_settings":[],"index_settings":{},"ml_settings":[],"ccr_auto_followed_system_indices":[]}""")) {
      LOGGER.warn("The ElasticSearch instance used during IT's is using deprecated features or "
        + "configuration. This is likely nothing to be really worried about (for example 'xpack.monitoring.enabled' "
        + "setting), but nevertheless it should be looked at to see if our docker image used during "
        + "integration tests needs updating for the next version of ElasticSearch. "
        + "See https://www.elastic.co/guide/en/elasticsearch/reference/current/migration-api-deprecation.html "
        + "for more information. This is the deprecation warning we received:\n\n"
        + response.contentAscii());
    }
  }
}
