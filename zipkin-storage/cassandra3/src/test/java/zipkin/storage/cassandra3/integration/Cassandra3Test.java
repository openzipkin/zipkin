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
package zipkin.storage.cassandra3.integration;

import com.datastax.driver.core.Session;
import java.io.IOException;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin.storage.cassandra3.Cassandra3Storage;
import zipkin.storage.cassandra3.InternalForTests;

@RunWith(Enclosed.class)
public class Cassandra3Test {

  @ClassRule
  public static LazyCassandra3Storage storage =
    new LazyCassandra3Storage("openzipkin/zipkin-cassandra:1.29.1", "test_zipkin3");

  public static class DependenciesTest extends CassandraDependenciesTest {
    @Override protected Cassandra3Storage storage() {
      return storage.get();
    }

    @Override public void clear() {
      InternalForTests.clear(storage());
    }
  }

  public static class SpanStoreTest extends CassandraSpanStoreTest {
    @Override protected Cassandra3Storage storage() {
      return storage.get();
    }

    @Override @Test(expected = AssertionError.class) /* TODO */
    public void getTrace_128() {
      super.getTrace_128();
    }

    @Override @Test(expected = AssertionError.class) /* TODO */
    public void searchingByAnnotationShouldFilterBeforeLimiting() {
      super.searchingByAnnotationShouldFilterBeforeLimiting();
    }

    @Override public void clear() throws IOException {
      InternalForTests.clear(storage());
    }
  }

  public static class SpanConsumerTest extends CassandraSpanConsumerTest {
    @Override protected Cassandra3Storage storage() {
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

  @Ignore("TODO: get this working or explain why not")
  public static class StrictTraceIdFalseTest extends CassandraStrictTraceIdFalseTest {
    @Override protected Cassandra3Storage storage() {
      return storage.get();
    }
  }
}
