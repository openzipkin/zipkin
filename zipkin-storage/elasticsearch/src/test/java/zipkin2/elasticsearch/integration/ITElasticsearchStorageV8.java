/*
 * Copyright 2015-2024 The OpenZipkin Authors
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static zipkin2.elasticsearch.integration.ElasticsearchExtension.index;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("docker")
class ITElasticsearchStorageV8 extends ITElasticsearchStorage {

  @RegisterExtension ElasticsearchExtension elasticsearch = new ElasticsearchExtension(8);

  @Override ElasticsearchExtension elasticsearch() {
    return elasticsearch;
  }

  @Nested
  class ITEnsureIndexTemplate extends zipkin2.elasticsearch.integration.ITEnsureIndexTemplate {
    @Override protected ElasticsearchStorage.Builder newStorageBuilder(TestInfo testInfo) {
      return elasticsearch().computeStorageBuilder().index(index(testInfo));
    }
  }
}
