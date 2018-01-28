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
package zipkin2.storage.cassandra.integration;

import com.datastax.driver.core.Session;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin2.storage.cassandra.CassandraStorage;
import zipkin2.storage.cassandra.CassandraStorageRule;
import zipkin2.storage.cassandra.InternalForTests;

@RunWith(Enclosed.class)
public class ITCassandraStorage {

  static CassandraStorageRule classRule() {
    return new CassandraStorageRule("openzipkin/zipkin-cassandra:2.4.1", "test_zipkin3");
  }

  public static class DependenciesTest extends CassandraDependenciesTest {
    @ClassRule public static CassandraStorageRule storage = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String keyspace() {
      return ITCassandraStorage.keyspace(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropKeyspace(storage.session(), keyspace());
    }

    @Override protected CassandraStorage.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }
  }

  public static class SpanStoreTest extends CassandraSpanStoreTest {
    @ClassRule public static CassandraStorageRule storage = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String keyspace() {
      return ITCassandraStorage.keyspace(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropKeyspace(storage.session(), keyspace());
    }

    @Override protected CassandraStorage.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }
  }

  public static class SpanConsumerTest extends CassandraSpanConsumerTest {
    @ClassRule public static CassandraStorageRule storage = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String keyspace() {
      return ITCassandraStorage.keyspace(testName);
    }

    @Before public void clear() {
      InternalForTests.dropKeyspace(storage.session(), keyspace());
    }

    @Override protected CassandraStorage.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }
  }

  public static class EnsureSchemaTest extends CassandraEnsureSchemaTest {
    @ClassRule public static CassandraStorageRule storage = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String keyspace() {
      return ITCassandraStorage.keyspace(testName);
    }

    @Before public void clear() {
      InternalForTests.dropKeyspace(storage.session(), keyspace());
    }

    @Override protected Session session() {
      return storage.session();
    }
  }

  public static class StrictTraceIdFalseTest extends CassandraStrictTraceIdFalseTest {
    @ClassRule public static CassandraStorageRule storage = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String keyspace() {
      return ITCassandraStorage.keyspace(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropKeyspace(storage.session(), keyspace());
    }

    @Override protected CassandraStorage.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }
  }

  static String keyspace(TestName testName){
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
