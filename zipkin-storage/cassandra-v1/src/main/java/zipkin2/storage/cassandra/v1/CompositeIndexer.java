/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.v1;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import zipkin2.Call;
import zipkin2.Span;

final class CompositeIndexer {

  private final Set<Indexer> indexers;
  // Shared across all threads as updates can come from any thread.
  // Shared for all indexes to make data management easier (ex. maximumSize)
  private final ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState;

  CompositeIndexer(CassandraStorage storage, CacheBuilderSpec spec, int indexTtl) {
    this.sharedState = CacheBuilder.from(spec).<PartitionKeyToTraceId, Pair>build().asMap();
    Indexer.Factory factory = new Indexer.Factory(storage.session(), indexTtl, sharedState);
    ImmutableSet.Builder<Indexer> indexers = ImmutableSet.builder();
    indexers.add(factory.create(new InsertTraceIdByServiceName(storage.bucketCount)));
    if (storage.metadata().hasRemoteService) {
      indexers.add(factory.create(new InsertTraceIdByRemoteServiceName()));
    }
    indexers.add(factory.create(new InsertTraceIdBySpanName()));
    indexers.add(factory.create(new InsertTraceIdByAnnotation(storage.bucketCount)));
    this.indexers = indexers.build();
  }

  void index(Span span, List<Call<Void>> calls) {
    for (Indexer indexer : indexers) indexer.index(span, calls);
  }

  public void clear() {
    sharedState.clear();
  }
}
