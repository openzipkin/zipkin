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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.MergeById;
import zipkin.internal.Util;
import zipkin.storage.DependenciesTest;
import zipkin.storage.InMemorySpanStore;
import zipkin.storage.InMemoryStorage;

import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.Util.midnightUTC;

public class CassandraDependenciesTest extends DependenciesTest {
  private final Cassandra3Storage storage;

  public CassandraDependenciesTest() {
    this.storage = Cassandra3TestGraph.INSTANCE.storage.get();
  }

  @Override protected Cassandra3Storage storage() {
    return storage;
  }

  @Override public void clear() {
    storage.clear();
  }

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
    new CassandraDependenciesWriter(storage.session.get()).write(links, midnight);
  }

  static final class CassandraDependenciesWriter {
    private final PreparedStatement insertDependencies;
    private final Session session;

    CassandraDependenciesWriter(Session session) {
      this.session = session;
      insertDependencies = session.prepare(QueryBuilder.insertInto(Schema.TABLE_DEPENDENCIES)
          .value("day", QueryBuilder.bindMarker("day"))
          .value("links", QueryBuilder.bindMarker("links")));
    }

    @VisibleForTesting void write(List<DependencyLink> links, long timestampMillis) {
      long midnight = Util.midnightUTC(timestampMillis);
      ByteBuffer thrift = ByteBuffer.wrap(Codec.THRIFT.writeDependencyLinks(links));
      Futures.getUnchecked(storeDependencies(midnight, thrift));
    }

    ListenableFuture<?> storeDependencies(long epochDayMillis, ByteBuffer links) {
      Date startFlooredToDay = new Date(epochDayMillis);
      try {
        BoundStatement bound = CassandraUtil.bindWithName(insertDependencies, "insert-dependencies")
            .setTimestamp("day", startFlooredToDay)
            .setBytes("links", links);

        return session.executeAsync(bound);
      } catch (RuntimeException ex) {
        return Futures.immediateFailedFuture(ex);
      }
    }
  }
}
