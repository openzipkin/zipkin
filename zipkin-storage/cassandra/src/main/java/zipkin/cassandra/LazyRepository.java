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

import com.datastax.driver.core.Cluster;
import zipkin.cassandra.internal.Repository;
import zipkin.internal.Lazy;

final class LazyRepository extends Lazy<Repository> implements AutoCloseable {
  private final ClusterProvider clusterProvider;
  private final String keyspace;
  private final boolean ensureSchema;

  LazyRepository(CassandraStorage.Builder builder) {
    this.clusterProvider = new ClusterProvider(builder);
    this.keyspace = builder.keyspace;
    this.ensureSchema = builder.ensureSchema;
  }

  @Override protected Repository compute() {
    Cluster cluster = clusterProvider.get();
    try {
      return new Repository(keyspace, cluster, ensureSchema);
    } catch (RuntimeException e) {
      cluster.close();
      throw e;
    }
  }

  @Override
  public void close() {
    Repository maybeNull = maybeGet();
    if (maybeNull != null) maybeNull.close();
  }
}
