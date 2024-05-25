/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static zipkin2.elasticsearch.integration.ElasticsearchExtension.index;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("docker")
class ITOpenSearchStorageV2 extends ITElasticsearchStorage {

  @RegisterExtension static OpenSearchExtension opensearch = new OpenSearchExtension(2);

  @Override OpenSearchExtension elasticsearch() {
    return opensearch;
  }

  @Nested
  class ITEnsureIndexTemplate extends zipkin2.elasticsearch.integration.ITEnsureIndexTemplate {
    @Override protected ElasticsearchStorage.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }
  }
}
