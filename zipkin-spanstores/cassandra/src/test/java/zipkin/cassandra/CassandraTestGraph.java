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

enum CassandraTestGraph {
  INSTANCE;

  static final CassandraConfig CONFIG = new CassandraConfig.Builder()
      .keyspace("test_zipkin_spanstore").build();

  static {
    // Ensure the repository's local cache of service names expire quickly
    System.setProperty("zipkin.store.cassandra.internal.writtenNamesTtl", "1");
  }

  private AssumptionViolatedException ex;
  private CassandraSpanStore spanStore;

  /** A lot of tech debt here because the repository constructor performs I/O. */
  synchronized CassandraSpanStore spanStore() {
    if (ex != null) throw ex;
    if (this.spanStore == null) {
      try {
        this.spanStore = new CassandraSpanStore(CONFIG);
      } catch (NoHostAvailableException e) {
        throw ex = new AssumptionViolatedException(e.getMessage());
      }
    }
    return spanStore;
  }
}
