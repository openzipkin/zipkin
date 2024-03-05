/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.integration;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.Internal;
import zipkin2.storage.ITStorage;
import zipkin2.storage.StorageComponent;

import static com.linecorp.armeria.common.HttpHeaderNames.CONTENT_TYPE;
import static com.linecorp.armeria.common.HttpMethod.DELETE;
import static com.linecorp.armeria.common.HttpMethod.GET;
import static com.linecorp.armeria.common.HttpMethod.PUT;
import static com.linecorp.armeria.common.MediaType.JSON_UTF_8;
import static zipkin2.TestObjects.spanBuilder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ITEnsureIndexTemplate extends ITStorage<ElasticsearchStorage> {
  @Override protected abstract ElasticsearchStorage.Builder newStorageBuilder(TestInfo testInfo);

  @Override protected void configureStorageForTest(StorageComponent.Builder storage) {
  }

  @Override protected boolean initializeStoragePerTest() {
    return true; // We need a different index pattern per test
  }

  @Override protected void clear() throws Exception {
    storage.clear();
  }

  @Test void createZipkinIndexTemplate_getTraces_returnsSuccess(TestInfo testInfo) throws Exception {
    String testSuffix = testSuffix(testInfo);
    storage = newStorageBuilder(testInfo).templatePriority(10).build();
    try {
      // Delete all templates in order to create the "catch-all" index template, because
      // ES does not allow multiple index templates of the same index_patterns and priority
      http(DELETE, "/_template/*");
      setUpCatchAllTemplate();

      // Implicitly creates an index template
      checkStorage();

      // Get all templates. We don't assert on this at the moment. This is for logging on ES_DEBUG.
      http(GET, "/_template");

      // Now, add a span, which should be indexed differently than default.
      Span span = spanBuilder(testSuffix).putTag("queryTest", "ok").build();
      accept(List.of(span));

      // Assert that Zipkin's templates work and source is returned
      assertGetTracesReturns(
        requestBuilder()
          .parseAnnotationQuery("queryTest=" + span.tags().get("queryTest"))
          .build(),
        List.of(span));
    } finally {
      // Delete "catch-all" index template, so it does not interfere with any other test
      http(DELETE, catchAllIndexPath());
    }
  }

  /**
   * Create a "catch-all" index template with the lowest priority prior to running tests to ensure
   * that the index templates created during tests with higher priority function as designed. Only
   * applicable for ES >= 7.8
   */
  void setUpCatchAllTemplate() throws IOException {
    AggregatedHttpRequest updateTemplate = AggregatedHttpRequest.of(
      RequestHeaders.of(PUT, catchAllIndexPath(), CONTENT_TYPE, JSON_UTF_8),
      HttpData.ofUtf8(catchAllTemplate()));
    Internal.instance.http(storage).newCall(updateTemplate, (parser, contentString) -> null,
      "update-template").execute();
  }

  String catchAllIndexPath() {
    return "/_index_template/catch-all";
  }

  /** Catch-all template doesn't store source */
  String catchAllTemplate() {
    return """
      {
        "index_patterns" : ["*"],
        "priority" : 5,
        "template": {
          "settings" : {
            "number_of_shards" : 1
          },
          "mappings" : {
            "_source": {"enabled": false }
          }
        }
      }\
      """;
  }

  void http(HttpMethod method, String path) throws IOException {
    AggregatedHttpRequest delete = AggregatedHttpRequest.of(method, path);
    Internal.instance.http(storage)
      .newCall(delete, (parser, contentString) -> null, method + "-" + path).execute();
  }
}
