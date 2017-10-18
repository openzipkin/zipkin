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

import org.junit.AssumptionViolatedException;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.StorageComponent;
import zipkin.storage.StrictTraceIdFalseTest;
import zipkin2.CheckResult;
import zipkin2.storage.cassandra.CassandraStorage;

abstract class CassandraStrictTraceIdFalseTest extends StrictTraceIdFalseTest {

  final CassandraStorage storage;

  CassandraStrictTraceIdFalseTest() {
    storage = storageBuilder()
      .strictTraceId(false)
      .keyspace("test_zipkin_http_mixed")
      .build();

    CheckResult check = storage.check();
    if (!check.ok()) {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
  }

  protected abstract CassandraStorage.Builder storageBuilder();

  @Override protected final StorageComponent storage() {
    return V2StorageComponent.create(storage);
  }
}
