/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.google.common.cache.CacheBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import zipkin2.Call;
import zipkin2.Span;

final class CompositeIndexer {

  private final Set<Indexer> indexers;
  // Shared across all threads as updates can come from any thread.
  // Shared for all indexes to make data management easier (ex. maximumSize)
  private final Map<PartitionKeyToTraceId, Pair> sharedState;

  CompositeIndexer(CassandraStorage storage, int indexTtl) {
    sharedState = CacheBuilder.newBuilder()
      .maximumSize(storage.indexCacheMax)
      .expireAfterWrite(storage.indexCacheTtl, TimeUnit.SECONDS)
      .<PartitionKeyToTraceId, Pair>build().asMap();
    Indexer.Factory factory = new Indexer.Factory(storage.session(), indexTtl, sharedState);
    indexers = new LinkedHashSet<>();
    indexers.add(factory.create(new InsertTraceIdByServiceName(storage.bucketCount)));
    if (storage.metadata().hasRemoteService) {
      indexers.add(factory.create(new InsertTraceIdByRemoteServiceName()));
    }
    indexers.add(factory.create(new InsertTraceIdBySpanName()));
    indexers.add(factory.create(new InsertTraceIdByAnnotation(storage.bucketCount)));
  }

  void index(Span span, List<Call<Void>> calls) {
    for (Indexer indexer : indexers) indexer.index(span, calls);
  }

  public void clear() {
    sharedState.clear();
  }
}
