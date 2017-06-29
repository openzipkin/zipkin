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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.storage.AsyncSpanConsumer;

import static zipkin.storage.guava.GuavaStorageAdapters.guavaToAsync;

public class InternalForTests {

  public static void clear(Cassandra3Storage storage) {
    storage.clear();
  }

  public static void writeDependencyLinks(Cassandra3Storage storage, List<DependencyLink> links,
    long midnightUTC) {

    ByteBuffer thrift = ByteBuffer.wrap(Codec.THRIFT.writeDependencyLinks(links));

    Date startFlooredToDay = new Date(midnightUTC);
    Statement statement = QueryBuilder.insertInto(Schema.TABLE_DEPENDENCIES)
      .value("day", startFlooredToDay)
      .value("links", thrift);

    storage.session().execute(statement);
  }

  public static int indexFetchMultiplier(Cassandra3Storage storage) {
    return storage.indexFetchMultiplier;
  }

  public static long rowCountForTraceByServiceSpan(Cassandra3Storage storage) {
    return rowCount(storage, Schema.TABLE_TRACE_BY_SERVICE_SPAN);
  }

  public static AsyncSpanConsumer withoutStrictTraceId(Cassandra3Storage storage) {
    return guavaToAsync(new CassandraSpanConsumer(storage.session(), false));
  }

  public static KeyspaceMetadata ensureExists(String keyspace, Session session) {
    return Schema.ensureExists(keyspace, session);
  }

  public static RetryPolicy zipkinRetryPolicy() {
    return ZipkinRetryPolicy.INSTANCE;
  }

  private static long rowCount(Cassandra3Storage storage, String table) {
    return storage.session().execute("SELECT COUNT(*) from " + table).one().getLong(0);
  }
}
