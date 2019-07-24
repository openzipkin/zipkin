/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInfo;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.InternalForTests;
import zipkin2.storage.StorageComponent;

import static zipkin2.elasticsearch.integration.ElasticsearchStorageExtension.index;

abstract class ITElasticsearchStorage {

  abstract ElasticsearchStorageExtension backend();

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITDependencies extends zipkin2.storage.ITDependencies<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return backend().computeStorageBuilder().index(index(testInfo));
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<Span> spans) {
      aggregateLinks(spans).forEach(
        (midnight, links) -> InternalForTests.writeDependencyLinks(
          storage, links, midnight));
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

}
