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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin.storage.cassandra.SessionFactory.Default.buildCluster;

@RunWith(Enclosed.class)
public class ITCassandraStorage {

  @ClassRule
  public static LazyCassandraStorage storage =
    new LazyCassandraStorage("openzipkin/zipkin-cassandra:2.4.1", "test_zipkin");

  public static class DependenciesTest extends CassandraDependenciesTest {
    @Override protected CassandraStorage storage() {
      return storage.get();
    }

    @Override public void clear() {
      storage().clear();
    }
  }

  public static class SpanStoreTest extends CassandraSpanStoreTest {
    @Override CassandraStorage.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }
  }

  public static class SpanConsumerTest extends CassandraSpanConsumerTest {
    @Override protected CassandraStorage storage() {
      return storage.get();
    }
  }

  public static class EnsureSchemaTest extends CassandraEnsureSchemaTest {

    @Rule public TestName name = new TestName();

    @Override protected TestName name() {
      return name;
    }

    @Override protected Session session() {
      return storage.get().session();
    }
  }

  public static class StrictTraceIdFalseTest extends CassandraStrictTraceIdFalseTest {
    @Override CassandraStorage.Builder storageBuilder() {
      storage.get();
      return storage.computeStorageBuilder();
    }
  }

  public static class WithOriginalSchemaSpanStoreTest extends CassandraSpanStoreTest {

    @Override CassandraStorage.Builder storageBuilder() {
      storage.get(); // initialize the cluster
      return storage.computeStorageBuilder().ensureSchema(false);
    }

    @Before public void installOldSchema() {
      try (Cluster cluster = buildCluster(storage());
           Session session = cluster.newSession()) {
        Schema.applyCqlFile(storage().keyspace, session, "/cassandra-schema-cql3-original.txt");
      }
    }

    /**
     * The PRIMARY KEY of {@link Tables#SERVICE_NAME_INDEX} doesn't consider trace_id, so will only
     * see bucket count traces to a service per millisecond.
     */
    @Override public void getTraces_manyTraces() {
      try {
        super.getTraces_manyTraces();
      } catch (AssertionError e) {
        assertThat(e).hasMessage("Expected size:<1000> but was:<10>");
      }
    }
  }
}
