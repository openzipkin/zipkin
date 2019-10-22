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
package zipkin2.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import zipkin2.CheckResult;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Base class for all {@link StorageComponent} integration tests. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ITStorage<T extends StorageComponent> {

  protected T storage;

  @BeforeAll void initializeStorage(TestInfo testInfo) {
    if (initializeStoragePerTest()) {
      return;
    }
    doInitializeStorage(testInfo);
  }

  @BeforeEach void initializeStorageForTest(TestInfo testInfo) {
    if (!initializeStoragePerTest()) {
      return;
    }
    doInitializeStorage(testInfo);
  }

  void doInitializeStorage(TestInfo testInfo) {
    StorageComponent.Builder builder = newStorageBuilder(testInfo);
    configureStorageForTest(builder);
    // TODO(anuraaga): It wouldn't be difficult to allow storage builders to be parameterized by
    // their storage type.
    @SuppressWarnings("unchecked")
    T storage = (T) builder.build();
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

  /**
   * Returns a new {@link StorageComponent.Builder} for connecting to the backend for the test.
   */
  protected abstract StorageComponent.Builder newStorageBuilder(TestInfo testInfo);

  /**
   * Configures a {@link StorageComponent.Builder} with parameters for the test being executed.
   */
  protected abstract void configureStorageForTest(StorageComponent.Builder storage);

  protected Traces traces() {
    return storage.traces();
  }

  protected SpanStore store() {
    return storage.spanStore();
  }

  protected ServiceAndSpanNames names() {
    return storage.serviceAndSpanNames();
  }

  protected final void accept(Span... spans) throws IOException {
    accept(asList(spans));
  }

  protected final void accept(List<Span> spans) throws IOException {
    for (int i = 0, length = spans.size(); i < length; i += 100) {
      storage.spanConsumer().accept(spans.subList(i, Math.min(length, i + 100))).execute();
      blockWhileInFlight();
    }
  }

  // Blocks between writes of 100 spans to help avoid readback problems.
  protected void blockWhileInFlight() {
  }

  /** Clears store between tests. */
  protected abstract void clear() throws Exception;

  static List<List<Span>> sortTraces(List<List<Span>> traces) {
    List<List<Span>> result = new ArrayList<>();
    for (List<Span> trace : traces) result.add(sortTrace(trace));
    Collections.sort(result, Comparator.comparing(o -> o.get(0).traceId()));
    return result;
  }

  static ArrayList<Span> sortTrace(List<Span> trace) {
    ArrayList<Span> result = new ArrayList<>(trace);
    result.sort((l, r) -> { // Include trace ID to ensure mixed-length traces have consistent order
      int traceId = l.traceId().compareTo(r.traceId());
      return (traceId == 0) ? Long.compare(l.timestampAsLong(), r.timestampAsLong()) : traceId;
    });
    return result;
  }
}
