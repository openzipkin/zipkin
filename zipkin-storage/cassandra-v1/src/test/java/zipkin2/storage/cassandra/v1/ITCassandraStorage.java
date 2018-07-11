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
package zipkin2.storage.cassandra.v1;

import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin2.Span;
import zipkin2.storage.StorageComponent;

import static zipkin2.storage.cassandra.v1.InternalForTests.dropKeyspace;
import static zipkin2.storage.cassandra.v1.InternalForTests.keyspace;
import static zipkin2.storage.cassandra.v1.InternalForTests.writeDependencyLinks;

@RunWith(Enclosed.class)
public class ITCassandraStorage {

  static CassandraStorageRule classRule() {
    return new CassandraStorageRule("openzipkin/zipkin-cassandra:2.4.6", "test_cassandra3");
  }

  public static class ITSpanStore extends zipkin2.storage.ITSpanStore {
    @ClassRule public static CassandraStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    @Override @Test @Ignore("Multiple services unsupported") public void getTraces_tags() {
    }

    @Override @Test @Ignore("Duration unsupported") public void getTraces_minDuration() {
    }

    @Override @Test @Ignore("Duration unsupported") public void getTraces_maxDuration() {
    }

    CassandraStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().keyspace(keyspace(testName)).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() {
      dropKeyspace(backend.session(), keyspace(testName));
    }
  }

  public static class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse {
    @ClassRule public static CassandraStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    CassandraStorage storage;

    @Before public void connect() {
      storage =
        backend.computeStorageBuilder().keyspace(keyspace(testName)).strictTraceId(false).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Before @Override public void clear() {
      dropKeyspace(backend.session(), keyspace(testName));
    }
  }

  public static class ITDependencies extends zipkin2.storage.ITDependencies {
    @ClassRule public static CassandraStorageRule backend = classRule();
    @Rule public TestName testName = new TestName();

    CassandraStorage storage;

    @Before public void connect() {
      storage = backend.computeStorageBuilder().keyspace(keyspace(testName)).build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<Span> spans) throws Exception {
      aggregateLinks(spans).forEach(
        (midnight, links) -> writeDependencyLinks(storage, links, midnight));
    }

    @Before @Override public void clear() {
      dropKeyspace(backend.session(), keyspace(testName));
    }
  }
}
