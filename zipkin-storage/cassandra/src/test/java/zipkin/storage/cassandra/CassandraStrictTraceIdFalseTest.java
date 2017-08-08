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

import zipkin.storage.StrictTraceIdFalseTest;

abstract class CassandraStrictTraceIdFalseTest extends StrictTraceIdFalseTest {

  private final CassandraStorage storage;

  CassandraStrictTraceIdFalseTest() {
    storage = storageBuilder()
        .strictTraceId(false)
        .keyspace("test_zipkin_mixed").build();
  }

  abstract CassandraStorage.Builder storageBuilder();

  @Override protected final CassandraStorage storage() {
    return storage;
  }

  @Override public void clear() {
    storage.clear();
  }
}
