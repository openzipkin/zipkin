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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1Span;

/**
 * Inserts index rows into Cassandra according to {@link IndexSupport} of a table. This skips
 * entries that don't improve results based on {@link QueryRequest#endTs} and {@link
 * QueryRequest#lookback}. For example, it doesn't insert rows that only vary on timestamp and exist
 * between timestamps of existing rows.
 */
final class Indexer {
  static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

  final PreparedStatement prepared;
  final TimestampCodec timestampCodec;
  final IndexSupport index;
  final Session session;

  /**
   * Shared across all threads, as updates to indexes can come from any thread. Null disables
   * optimization.
   */
  @Nullable private final ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState;

  Indexer(
      Session session,
      int indexTtl,
      @Nullable ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState,
      IndexSupport index) {
    this.index = index;
    Insert insert =
        index.declarePartitionKey(
            QueryBuilder.insertInto(index.table())
                .value("ts", QueryBuilder.bindMarker("ts"))
                .value("trace_id", QueryBuilder.bindMarker("trace_id")));
    if (indexTtl > 0) insert.using(QueryBuilder.ttl(indexTtl));
    this.prepared = session.prepare(insert);
    this.session = session;
    this.timestampCodec = new TimestampCodec(session);
    this.sharedState = sharedState;
  }

  @AutoValue
  abstract static class Input {
    abstract long trace_id();

    abstract long ts();

    abstract String partitionKey();
  }

  final class IndexCall extends ResultSetFutureCall {

    final Input input;

    IndexCall(Input input) {
      this.input = input;
    }

    @Override
    protected ResultSetFuture newFuture() {
      BoundStatement bound =
          prepared
              .bind()
              .setLong("trace_id", input.trace_id())
              .setBytesUnsafe("ts", timestampCodec.serialize(input.ts()));

      index.bindPartitionKey(bound, input.partitionKey());

      return session.executeAsync(bound);
    }

    @Override
    public String toString() {
      return input.toString().replace("Input", "IndexTrace");
    }

    @Override
    public IndexCall clone() {
      return new IndexCall(input);
    }
  }

  void index(List<V1Span> spans, List<Call<ResultSet>> calls) {
    // First parse each span into partition keys used to support query requests
    Builder<PartitionKeyToTraceId, Long> parsed = ImmutableSetMultimap.builder();
    for (V1Span span : spans) {
      Long timestamp = guessTimestamp(span);
      if (timestamp == null) continue;
      for (String partitionKey : index.partitionKeys(span)) {
        parsed.put(
            new PartitionKeyToTraceId(index.table(), partitionKey, span.traceId()),
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
    for (Map.Entry<PartitionKeyToTraceId, Long> entry : toInsert.entries()) {
      calls.add(
          new IndexCall(
              new AutoValue_Indexer_Input(
                  entry.getKey().traceId, entry.getValue(), entry.getKey().partitionKey)));
    }
  }

  @VisibleForTesting
  static ImmutableSetMultimap<PartitionKeyToTraceId, Long> entriesThatIncreaseGap(
      ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState,
      ImmutableSetMultimap<PartitionKeyToTraceId, Long> updates) {
    ImmutableSet.Builder<PartitionKeyToTraceId> toUpdate = ImmutableSet.builder();

    // Enter a loop that affects shared state when an update widens the time interval for a key.
    for (Map.Entry<PartitionKeyToTraceId, Long> input : updates.entries()) {
      PartitionKeyToTraceId key = input.getKey();
      long timestamp = input.getValue();
      for (; ; ) {
        Pair oldRange = sharedState.get(key);
        if (oldRange == null) {
          // Initial state is where this key has a single timestamp.
          oldRange = sharedState.putIfAbsent(key, new Pair(timestamp, timestamp));

          // If there was no previous value, we need to update the index
          if (oldRange == null) {
            toUpdate.add(key);
            break;
          }
        }

        long first = timestamp < oldRange.left ? timestamp : oldRange.left;
        long last = timestamp > oldRange.right ? timestamp : oldRange.right;

        Pair newRange = new Pair(first, last);
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
      Pair firstLast = sharedState.get(needsUpdate);
      if (updates.containsEntry(needsUpdate, firstLast.left))
        result.put(needsUpdate, firstLast.left);
      if (updates.containsEntry(needsUpdate, firstLast.right))
        result.put(needsUpdate, firstLast.right);
    }
    return result.build();
  }

  interface IndexSupport {

    String table();

    Insert declarePartitionKey(Insert insert);

    BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey);

    Set<String> partitionKeys(V1Span span);
  }

  static class Factory {

    private final Session session;
    private final int indexTtl;
    private final ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState;

    public Factory(
        Session session,
        int indexTtl,
        @Nullable ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState) {
      this.session = session;
      this.indexTtl = indexTtl;
      this.sharedState = sharedState;
    }

    Indexer create(IndexSupport index) {
      return new Indexer(session, indexTtl, sharedState, index);
    }
  }

  /**
   * Instrumentation should set timestamp when recording a span so that guess-work isn't needed.
   * Since a lot of instrumentation don't, we have to make some guesses.
   *
   * <pre><ul>
   *   <li>If there is a "cs" annotation, use that</li>
   *   <li>Fall back to "sr" annotation</li>
   *   <li>Otherwise, return null</li>
   * </ul></pre>
   */
  static Long guessTimestamp(V1Span span) {
    if (span.timestamp() != 0L || span.annotations().isEmpty()) return span.timestamp();
    Long rootServerRecv = null;
    for (int i = 0, length = span.annotations().size(); i < length; i++) {
      V1Annotation annotation = span.annotations().get(i);
      if (annotation.value().equals("cs")) {
        return annotation.timestamp();
      } else if (annotation.value().equals("sr")) {
        rootServerRecv = annotation.timestamp();
      }
    }
    return rootServerRecv;
  }
}
