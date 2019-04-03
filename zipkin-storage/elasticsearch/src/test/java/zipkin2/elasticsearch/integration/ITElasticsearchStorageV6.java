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
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.InternalForTests;
import zipkin2.storage.StorageComponent;

import static zipkin2.elasticsearch.integration.ElasticsearchStorageRule.index;

@RunWith(Enclosed.class)
public class ITElasticsearchStorageV6 {

  static ElasticsearchStorageRule classRule() {
    return new ElasticsearchStorageRule("openzipkin/zipkin-elasticsearch6:2.12.7",
      "test_elasticsearch3");
  }

  public static class ITSpanStore extends zipkin2.storage.ITSpanStore {
    @ClassRule public static ElasticsearchStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    ElasticsearchStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().index(index(testName)).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override @Test @Ignore("No consumer-side span deduplication") public void deduplicates() {
    }

    @Before @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  public static class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse {
    @ClassRule public static ElasticsearchStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    ElasticsearchStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().index(index(testName))
        .searchEnabled(false).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  public static class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags {
    @ClassRule public static ElasticsearchStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected StorageComponent.Builder storageBuilder() {
      return backend.computeStorageBuilder().index(index(testName));
    }

    @Before @Override public void clear() throws IOException {
      ((ElasticsearchStorage) storage).clear();
    }
  }

  public static class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse {
    @ClassRule public static ElasticsearchStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    ElasticsearchStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().index(index(testName)).strictTraceId(false).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  public static class ITDependencies extends zipkin2.storage.ITDependencies {
    @ClassRule public static ElasticsearchStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    ElasticsearchStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().index(index(testName)).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<Span> spans) throws Exception {
      aggregateLinks(spans).forEach(
        (midnight, links) -> InternalForTests.writeDependencyLinks(storage, links, midnight));
    }

    @Before @Override public void clear() throws IOException {
      storage.clear();
    }
  }
}
