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
package zipkin2.storage.influxdb.integration;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import zipkin2.storage.influxdb.InfluxDBStorage;
import zipkin2.storage.influxdb.InfluxDBStorageRule;
import zipkin2.storage.influxdb.InternalForTests;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class ITInfluxDBStorage {

  /** Written intentionally to allow you to run a single nested method via the CLI. See README */
  static InfluxDBStorageRule classRule() {
    return new InfluxDBStorageRule("influxdb:1.4.1-alpine", "test_zipkin3");
  }

  // This test is temporary playground to be removed later once things are implemented
  public static class BabyStepsTest {
    @ClassRule public static InfluxDBStorageRule influxdb = classRule();
    @Rule public TestName testName = new TestName();

    @Test public void checkWorks() {
      try (InfluxDBStorage storage = influxdb.computeStorageBuilder()
        .database(database(testName)).build()) {

        assertThat(storage.check().ok()).isTrue();
      }
    }
  }

  @Ignore("Ignored as we know all tests will fail")
  public static class DependenciesTest extends InfluxDBDependenciesTest {
    @ClassRule public static InfluxDBStorageRule influxdb = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String database() {
      return ITInfluxDBStorage.database(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropDatabase(v2Storage(), database());
    }

    @Override protected InfluxDBStorage.Builder storageBuilder() {
      return influxdb.computeStorageBuilder();
    }
  }

  @Ignore("Ignored as we know all tests will fail")
  public static class SpanStoreTest extends InfluxDBSpanStoreTest {
    @ClassRule public static InfluxDBStorageRule influxdb = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String database() {
      return ITInfluxDBStorage.database(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropDatabase(v2Storage(), database());
    }

    @Override protected InfluxDBStorage.Builder storageBuilder() {
      return influxdb.computeStorageBuilder();
    }
  }

  @Ignore("Ignored as we know all tests will fail")
  public static class StrictTraceIdFalseTest extends InfluxDBStrictTraceIdFalseTest {
    @ClassRule public static InfluxDBStorageRule influxdb = classRule();
    @Rule public TestName testName = new TestName();

    @Override protected String database() {
      return ITInfluxDBStorage.database(testName);
    }

    @Before @Override public void clear() {
      InternalForTests.dropDatabase(v2Storage(), database());
    }

    @Override protected InfluxDBStorage.Builder storageBuilder() {
      return influxdb.computeStorageBuilder();
    }
  }

  static String database(TestName testName) {
    String result = testName.getMethodName().toLowerCase();
    return result.length() <= 48 ? result : result.substring(result.length() - 48);
  }
}
