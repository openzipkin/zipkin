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
package zipkin.storage.cassandra;

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
import zipkin.DependencyLink;
import zipkin.internal.Dependencies;
import zipkin.internal.Util;

final class CassandraDependenciesWriter {
  private final PreparedStatement insertDependencies;
  private final Session session;

  CassandraDependenciesWriter(Session session) {
    this.session = session;
    insertDependencies = session.prepare(QueryBuilder.insertInto("dependencies")
        .value("day", QueryBuilder.bindMarker("day"))
        .value("dependencies", QueryBuilder.bindMarker("dependencies")));
  }

  @VisibleForTesting void write(List<DependencyLink> links, long timestampMillis) {
    long midnight = Util.midnightUTC(timestampMillis);
    Dependencies deps = Dependencies.create(midnight, midnight /* ignored */, links);
    ByteBuffer thrift = deps.toThrift();
    Futures.getUnchecked(storeDependencies(midnight, thrift));
  }

  ListenableFuture<?> storeDependencies(long epochDayMillis, ByteBuffer dependencies) {
    Date startFlooredToDay = new Date(epochDayMillis);
    try {
      BoundStatement bound = CassandraUtil.bindWithName(insertDependencies, "insert-dependencies")
          .setTimestamp("day", startFlooredToDay)
          .setBytes("dependencies", dependencies);

      return session.executeAsync(bound);
    } catch (RuntimeException ex) {
      return Futures.immediateFailedFuture(ex);
    }
  }
}
