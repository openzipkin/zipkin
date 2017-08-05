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
package zipkin.storage.mysql;

import org.junit.ClassRule;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.storage.StorageComponent;

@RunWith(Enclosed.class)
public class MySQLTest {

  @ClassRule
  public static LazyMySQLStorage storage = new LazyMySQLStorage("1.29.1");

  public static class DependenciesTest extends zipkin.storage.DependenciesTest {

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class SpanStoreTest extends zipkin.storage.SpanStoreTest {

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override
    public void clear() {
      storage.get().clear();
    }
  }

  public static class StrictTraceIdFalseTest extends zipkin.storage.StrictTraceIdFalseTest {

    private final MySQLStorage storage;

    public StrictTraceIdFalseTest() {
      this.storage = MySQLTest.storage.computeStorageBuilder()
          .strictTraceId(false)
          .build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override
    public void clear() {
      storage.clear();
    }
  }


}
