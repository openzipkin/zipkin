package zipkin2.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import zipkin2.CheckResult;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Base class for all {@link StorageComponent} integration tests. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ITStorage<T extends StorageComponent> {

  protected T storage;

  @BeforeAll void initializeStorage() {
    if (initializeStoragePerTest()) {
      return;
    }
    doInitializeStorage();
  }

  @BeforeEach void initializeStorageForTest() {
    if (!initializeStoragePerTest()) {
      return;
    }
    doInitializeStorage();
  }

  void doInitializeStorage() {
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
    if (initializeStoragePerTest()) {
      return;
    }
    storage.close();
  }

  @AfterEach void closeStorageForTest() throws Exception {
    if (!initializeStoragePerTest()) {
      return;
    }
    storage.close();
  }

  @AfterEach void clearStorage() throws Exception {
    clear();
  }

  /**
   * Sets the test to initialise the {@link StorageComponent} before each test rather than the test
   * class. Generally, tests will run faster if the storage is initialized as infrequently as
   * possibly while clearing data between runs, but for certain backends like Cassandra, it's
   * difficult to reliably clear data between runs and tends to be very slow anyways.
   */
  protected boolean initializeStoragePerTest() {
    return false;
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
