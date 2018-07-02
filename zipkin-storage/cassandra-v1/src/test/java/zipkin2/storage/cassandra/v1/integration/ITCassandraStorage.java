/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1.integration;

import com.datastax.driver.core.KeyspaceMetadata;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.cassandra.v1.CassandraStorage;
import zipkin2.storage.cassandra.v1.CassandraStorageRule;
import zipkin2.storage.cassandra.v1.InternalForTests;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.cassandra.v1.InternalForTests.dropKeyspace;
import static zipkin2.storage.cassandra.v1.InternalForTests.keyspace;

@RunWith(Enclosed.class)
public class ITCassandraStorage {

  static CassandraStorageRule classRule() {
    return new CassandraStorageRule("openzipkin/zipkin-cassandra:2.4.6", "test_cassandra3");
  }

  public static class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse {
    @ClassRule public static CassandraStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    CassandraStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().keyspace(keyspace(testName))
        .strictTraceId(false).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() {
      dropKeyspace(backend.session(), keyspace(testName));
    }
  }

  public static class ITSpanStore extends zipkin2.storage.ITSpanStore {
    @ClassRule public static CassandraStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    @Override @Test @Ignore("Multiple services unsupported") public void getTraces_tags()
      throws Exception {
      super.getTraces_tags();
    }

    @Override @Test @Ignore("Duration unsupported") public void getTraces_minDuration()
      throws Exception {
      super.getTraces_minDuration();
    }

    @Override @Test @Ignore("Duration unsupported") public void getTraces_maxDuration()
      throws Exception {
      super.getTraces_maxDuration();
    }

    CassandraStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().keyspace(keyspace(testName))
        .build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() {
      dropKeyspace(backend.session(), keyspace(testName));
    }
  }
}
