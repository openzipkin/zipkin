/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.cassandra;

import com.google.common.util.concurrent.Futures;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.twitter.zipkin.storage.cassandra.Repository;
import zipkin.DependenciesTest;
import zipkin.DependencyLink;
import zipkin.InMemorySpanStore;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.internal.Dependencies;

import static zipkin.StorageAdapters.asyncToBlocking;
import static zipkin.internal.Util.midnightUTC;
import static zipkin.spanstore.guava.GuavaStorageAdapters.guavaToAsync;

public class CassandraDependenciesTest extends DependenciesTest {

  @Override protected SpanStore store() {
    return asyncToBlocking(guavaToAsync(CassandraTestGraph.INSTANCE.spanStore()));
  }

  @Override
  public void clear() {
    CassandraTestGraph.INSTANCE.spanStore().clear();
  }

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links.
   *
   * <p>This uses {@link InMemorySpanStore} to prepare links and {@link
   * Repository#storeDependencies(long, ByteBuffer)} to store them.
   *
   * <p>Note: The zipkin-dependencies-spark doesn't use any of these classes: it reads and writes to
   * the keyspace directly.
   */
  @Override
  public void processDependencies(List<Span> spans) {
    InMemorySpanStore mem = new InMemorySpanStore();
    mem.accept(spans);
    List<DependencyLink> links = mem.getDependencies(today + TimeUnit.DAYS.toMillis(1), null);

    long midnight = midnightUTC(spans.get(0).timestamp / 1000);
    Dependencies deps = Dependencies.create(midnight, midnight /* ignored */, links);
    ByteBuffer thrift = deps.toThrift();
    // Block on the future to get read-your-writes consistency during tests
    Futures.getUnchecked(CassandraTestGraph.INSTANCE.spanStore().repository.storeDependencies(midnight, thrift));
  }
}
