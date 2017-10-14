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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.storage.QueryRequest;

import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.storage.cassandra.CassandraUtil.bindWithName;

/**
 * Inserts index rows into Cassandra according to {@link IndexSupport} of a table. This skips
 * entries that don't improve results based on {@link QueryRequest#endTs} and {@link
 * QueryRequest#lookback}. For example, it doesn't insert rows that only vary on timestamp and exist
 * between timestamps of existing rows.
 */
final class Indexer {

  private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);
  private final PreparedStatement prepared;
  private final TimestampCodec timestampCodec;
  private final String boundName;
  private final IndexSupport index;
  @Nullable
  private final Integer indexTtl;
  private final Session session;
  /**
   * Shared across all threads, as updates to indexes can come from any thread. Null disables
   * optimization.
   */
  @Nullable
  private final ConcurrentMap<PartitionKeyToTraceId, Pair<Long>> sharedState;

  Indexer(Session session, @Nullable Integer indexTtl,
      @Nullable ConcurrentMap<PartitionKeyToTraceId, Pair<Long>> sharedState, IndexSupport index) {
    this.index = index;
    this.boundName = UPPER_CAMEL.to(LOWER_HYPHEN, index.getClass().getSimpleName());
    Insert insert = index.declarePartitionKey(QueryBuilder.insertInto(index.table())
        .value("ts", QueryBuilder.bindMarker("ts"))
        .value("trace_id", QueryBuilder.bindMarker("trace_id")));
    if (indexTtl != null) {
      insert.using(QueryBuilder.ttl(QueryBuilder.bindMarker("ttl_")));
    }
    this.prepared = session.prepare(insert);
    this.indexTtl = indexTtl;
    this.session = session;
    this.timestampCodec = new TimestampCodec(session);
    this.sharedState = sharedState;
  }

  ImmutableSet<ListenableFuture<?>> index(List<Span> spans) {
    // First parse each span into partition keys used to support query requests
    Builder<PartitionKeyToTraceId, Long> parsed = ImmutableSetMultimap.builder();
    for (Span span : spans) {
      Long timestamp = guessTimestamp(span);
      if (timestamp == null) continue;
      for (String partitionKey : index.partitionKeys(span)) {
        parsed.put(new PartitionKeyToTraceId(index.table(), partitionKey, span.traceId),
            1000 * (timestamp / 1000)); // index precision is millis
      }
    }

    // The parsed results may include inserts that already occur, or are redundant as they don't
    // impact QueryRequest.endTs or QueryRequest.loopback. For example, a parsed timestamp could
    // be between timestamps of rows that already exist for a particular trace.
    ImmutableSetMultimap<PartitionKeyToTraceId, Long> maybeInsert = parsed.build();

    ImmutableSetMultimap<PartitionKeyToTraceId, Long> toInsert;
    if (sharedState == null) { // special-case when caching is disabled.
      toInsert = maybeInsert;
    } else {
      // Optimized results will be smaller when the input includes traces with local spans, or when
      // other threads indexed the same trace.
      toInsert = entriesThatIncreaseGap(sharedState, maybeInsert);

      if (maybeInsert.size() > toInsert.size() && LOG.isDebugEnabled()) {
        int delta = maybeInsert.size() - toInsert.size();
        LOG.debug("optimized out {}/{} inserts into {}", delta, maybeInsert.size(), index.table());
      }
    }

    // For each entry, insert a new row in the index table asynchronously
    ImmutableSet.Builder<ListenableFuture<?>> result = ImmutableSet.builder();
    for (Map.Entry<PartitionKeyToTraceId, Long> entry : toInsert.entries()) {
      BoundStatement bound = bindWithName(prepared, boundName)
          .setLong("trace_id", entry.getKey().traceId)
          .setBytesUnsafe("ts", timestampCodec.serialize(entry.getValue()));
      if (indexTtl != null) {
        bound.setInt("ttl_", indexTtl);
      }
      index.bindPartitionKey(bound, entry.getKey().partitionKey);
      result.add(session.executeAsync(bound));
    }
    return result.build();
  }

  @VisibleForTesting
  static ImmutableSetMultimap<PartitionKeyToTraceId, Long> entriesThatIncreaseGap(
      ConcurrentMap<PartitionKeyToTraceId, Pair<Long>> sharedState,
      ImmutableSetMultimap<PartitionKeyToTraceId, Long> updates) {
    ImmutableSet.Builder<PartitionKeyToTraceId> toUpdate = ImmutableSet.builder();

    // Enter a loop that affects shared state when an update widens the time interval for a key.
    for (Map.Entry<PartitionKeyToTraceId, Long> input : updates.entries()) {
      PartitionKeyToTraceId key = input.getKey();
      long timestamp = input.getValue();
      for (; ; ) {
        Pair<Long> oldRange = sharedState.get(key);
        if (oldRange == null) {
          // Initial state is where this key has a single timestamp.
          oldRange = sharedState.putIfAbsent(key, Pair.create(timestamp, timestamp));

          // If there was no previous value, we need to update the index
          if (oldRange == null) {
            toUpdate.add(key);
            break;
          }
        }

        long first = timestamp < oldRange._1 ? timestamp : oldRange._1;
        long last = timestamp > oldRange._2 ? timestamp : oldRange._2;

        Pair<Long> newRange = Pair.create(first, last);
        if (oldRange.equals(newRange)) {
          break; // the current timestamp is contained
        } else if (sharedState.replace(key, oldRange, newRange)) {
          toUpdate.add(key); // The range was extended
          break;
        }
      }
    }

    // When the loop completes, we'll know one of our updates widened the interval of a trace, if
    // it is the first or last timestamp. By ignoring those between an existing interval, we can
    // end up with less Cassandra writes.
    Builder<PartitionKeyToTraceId, Long> result = ImmutableSetMultimap.builder();
    for (PartitionKeyToTraceId needsUpdate : toUpdate.build()) {
      Pair<Long> firstLast = sharedState.get(needsUpdate);
      if (updates.containsEntry(needsUpdate, firstLast._1)) result.put(needsUpdate, firstLast._1);
      if (updates.containsEntry(needsUpdate, firstLast._2)) result.put(needsUpdate, firstLast._2);
    }
    return result.build();
  }

  interface IndexSupport {

    String table();

    Insert declarePartitionKey(Insert insert);

    BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey);

    Set<String> partitionKeys(Span span);
  }

  static class Factory {

    private final Session session;
    private final Integer indexTtl;
    private final ConcurrentMap<PartitionKeyToTraceId, Pair<Long>> sharedState;

    public Factory(Session session, @Nullable Integer indexTtl,
        @Nullable ConcurrentMap<PartitionKeyToTraceId, Pair<Long>> sharedState) {
      this.session = session;
      this.indexTtl = indexTtl;
      this.sharedState = sharedState;
    }

    Indexer create(IndexSupport index) {
      return new Indexer(session, indexTtl, sharedState, index);
    }
  }
}
