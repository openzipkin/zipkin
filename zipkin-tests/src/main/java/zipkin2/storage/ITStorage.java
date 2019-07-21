package zipkin2.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import zipkin2.CheckResult;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Base class for all {@link StorageComponent} integration tests. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ITStorage<T extends StorageComponent> {

  protected T storage;

  @BeforeAll void initializeStorage() {
    // TODO(anuraaga): It wouldn't be difficult to allow storage builders to be parameterized by
    // their storage type.
    @SuppressWarnings("unchecked")
    T storage = (T) storageBuilder().build();
    this.storage = storage;

    CheckResult check = storage.check();
    assumeTrue(check.ok(), () -> "Could not connect to storage, skipping test: "
      + check.error().getMessage());
  }

  @AfterAll void closeStorage() throws Exception {
    storage.close();
  }

  @AfterEach void clearStorage() throws Exception {
    clear();
  }

  protected abstract StorageComponent.Builder storageBuilder();

  protected SpanStore store() {
    return storage.spanStore();
  }

  protected ServiceAndSpanNames names() {
    return storage.serviceAndSpanNames();
  }

  /** Clears store between tests. */
  protected abstract void clear() throws Exception;
}
