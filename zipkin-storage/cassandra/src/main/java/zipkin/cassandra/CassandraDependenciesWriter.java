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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.Bytes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.DependencyLink;
import zipkin.internal.Dependencies;
import zipkin.internal.Util;

import static zipkin.cassandra.CassandraUtil.iso8601;

final class CassandraDependenciesWriter {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraDependenciesWriter.class);

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
      BoundStatement bound = insertDependencies.bind()
          .setTimestamp("day", startFlooredToDay)
          .setBytes("dependencies", dependencies);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugInsertDependencies(startFlooredToDay, dependencies));
      }
      return session.executeAsync(bound);
    } catch (RuntimeException ex) {
      LOG.error("failed " + debugInsertDependencies(startFlooredToDay, dependencies), ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  private String debugInsertDependencies(Date startFlooredToDay, ByteBuffer dependencies) {
    return insertDependencies.getQueryString()
        .replace(":day", iso8601(startFlooredToDay.getTime() * 1000))
        .replace(":dependencies", Bytes.toHexString(dependencies));
  }
}
