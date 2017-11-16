/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin2.storage.influxdb.integration;

import java.util.List;
import org.junit.Before;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.MergeById;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.DependenciesTest;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.StorageComponent;
import zipkin2.storage.influxdb.InfluxDBStorage;
import zipkin2.storage.influxdb.InternalForTests;

import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.midnightUTC;

abstract class InfluxDBDependenciesTest extends DependenciesTest {

  abstract protected String database();

  protected final InfluxDBStorage v2Storage() {
    return storage;
  }

  private InfluxDBStorage storage;

  @Before public void connect() {
    storage = storageBuilder().database(database()).build();
  }

  protected abstract InfluxDBStorage.Builder storageBuilder();

  @Override protected final StorageComponent storage() {
    return V2StorageComponent.create(storage);
  }

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links.
   *
   * <p>Note: The zipkin-dependencies job doesn't use any of these classes: it reads and writes to
   * the database directly.
   */
  @Override
  public void processDependencies(List<Span> spans) {
    InMemoryStorage mem = new InMemoryStorage();
    mem.spanConsumer().accept(spans);
    List<DependencyLink> links = mem.spanStore().getDependencies(TODAY + DAY, null);

    // This gets or derives a timestamp from the spans
    long midnight = midnightUTC(guessTimestamp(MergeById.apply(spans).get(0)) / 1000);
    InternalForTests.writeDependencyLinks(storage, links, midnight);
  }
}
