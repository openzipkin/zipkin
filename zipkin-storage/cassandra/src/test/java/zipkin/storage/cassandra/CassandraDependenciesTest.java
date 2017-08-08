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
package zipkin.storage.cassandra;

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.MergeById;
import zipkin.storage.DependenciesTest;
import zipkin.storage.InMemorySpanStore;
import zipkin.storage.InMemoryStorage;

import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.Util.midnightUTC;

abstract class CassandraDependenciesTest extends DependenciesTest {

  @Override abstract protected CassandraStorage storage();

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links.
   *
   * <p>This uses {@link InMemorySpanStore} to prepare links and {@link CassandraDependenciesWriter}
   * to store them.
   *
   * <p>Note: The zipkin-dependencies-spark doesn't use any of these classes: it reads and writes to
   * the keyspace directly.
   */
  @Override
  public void processDependencies(List<Span> spans) {
    InMemoryStorage mem = new InMemoryStorage();
    mem.spanConsumer().accept(spans);
    List<DependencyLink> links = mem.spanStore().getDependencies(TODAY + DAY, null);

    // This gets or derives a timestamp from the spans
    long midnight = midnightUTC(MergeById.apply(spans).get(0).timestamp / 1000);
    new CassandraDependenciesWriter(storage().session.get()).write(links, midnight);
  }
}
