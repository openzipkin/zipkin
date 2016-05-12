/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.cassandra;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import org.junit.AssumptionViolatedException;
import zipkin.internal.LazyCloseable;

enum CassandraTestGraph {
  INSTANCE;

  static {
    // Ensure the repository's local cache of service names expire quickly
    System.setProperty("zipkin.store.cassandra.internal.writtenNamesTtl", "1");
  }

  final LazyCloseable<CassandraStorage> storage = new LazyCloseable<CassandraStorage>() {
    AssumptionViolatedException ex = null;

    @Override protected CassandraStorage compute() {
      if (ex != null) throw ex;
      CassandraStorage result = new CassandraStorage.Builder().keyspace("test_zipkin").build();
      try {
        result.spanStore().getServiceNames();
        return result;
      } catch (NoHostAvailableException e) {
        throw ex = new AssumptionViolatedException(e.getMessage());
      }
    }
  };
}
