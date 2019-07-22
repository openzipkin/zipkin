package zipkin2.elasticsearch.integration;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import zipkin2.Span;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.InternalForTests;
import zipkin2.storage.StorageComponent;

abstract class ITElasticsearchStorage {

  abstract ElasticsearchStorageExtension backend();

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }

  @Nested
  class ITDependencies extends zipkin2.storage.ITDependencies<ElasticsearchStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder() {
      return backend().computeStorageBuilder();
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
