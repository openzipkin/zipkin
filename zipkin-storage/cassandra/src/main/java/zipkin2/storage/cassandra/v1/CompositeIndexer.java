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

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import zipkin2.Call;
import zipkin2.v1.V1Span;

final class CompositeIndexer {

  private final Set<Indexer> indexers;
  // Shared across all threads as updates can come from any thread.
  // Shared for all indexes to make data management easier (ex. maximumSize)
  private final ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState;

  CompositeIndexer(Session session, CacheBuilderSpec spec, int bucketCount, int indexTtl) {
    this.sharedState = CacheBuilder.from(spec).<PartitionKeyToTraceId, Pair>build().asMap();
    Indexer.Factory factory = new Indexer.Factory(session, indexTtl, sharedState);
    this.indexers =
        ImmutableSet.of(
            factory.create(new InsertTraceIdByServiceName(bucketCount)),
            factory.create(new InsertTraceIdBySpanName()),
            factory.create(new InsertTraceIdByAnnotation(bucketCount)));
  }

  void index(List<V1Span> spans, List<Call<ResultSet>> calls) {
    for (Indexer optimizer : indexers) {
      optimizer.index(spans, calls);
    }
  }

  public void clear() {
    sharedState.clear();
  }
}
