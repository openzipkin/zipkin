/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInfo;

class ITInMemoryStorage {
  @Nested
  class ITTraces extends zipkin2.storage.ITTraces<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStoreHeavy extends zipkin2.storage.ITSpanStoreHeavy<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependencies extends zipkin2.storage.ITDependencies<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesHeavy extends zipkin2.storage.ITDependenciesHeavy<InMemoryStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return InMemoryStorage.newBuilder();
    }

    @Override public void clear() {
      storage.clear();
    }
  }
}
