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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheBuilder;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

/**
 * Inserts index rows into a Cassandra table. This skips entries that don't improve results based on
 * {@link QueryRequest#endTs()} and {@link QueryRequest#lookback()}. For example, it doesn't insert
 * rows that only vary on timestamp and exist between timestamps of existing rows.
 */
final class IndexTraceId extends ResultSetFutureCall<Void> {
  static final Logger LOG = LoggerFactory.getLogger(IndexTraceId.class);

  @AutoValue
  abstract static class Input {
    static Input create(String partitionKey, long timestamp, long traceId) {
      return new AutoValue_IndexTraceId_Input(partitionKey, timestamp, traceId);
    }

    abstract String partitionKey(); // ends up as a partition key, ignoring bucketing

    abstract long ts(); // microseconds at millis precision

    abstract long trace_id(); // clustering key
  }

  /** Deduplicates redundant entries upon {@link #iterator()} */
  interface Indexer extends Iterable<IndexTraceId.Input> {
    Indexer NOOP = new Indexer() {
      @Override public void add(Input input) {
      }

      @Override public Iterator<Input> iterator() {
        return Collections.emptyIterator();
      }
    };

    void add(IndexTraceId.Input input);
  }

  static abstract class Factory extends DeduplicatingVoidCallFactory<Input> {
    final Session session;
    // Shared across all threads as updates can come from any thread.
    final Map<Entry<String, Long>, Pair> sharedState;
    final String table;
    final int bucketCount;
    final PreparedStatement preparedStatement;
    final TimestampCodec timestampCodec;

    Factory(CassandraStorage storage, String table, int indexTtl) {
      super(TimeUnit.SECONDS.toMillis(storage.indexCacheTtl), storage.indexCacheTtl);
      session = storage.session();
      // TODO: this state should be merged with the DelayLimiter setup in the super class
      sharedState = CacheBuilder.newBuilder()
        .maximumSize(storage.indexCacheMax)
        .expireAfterWrite(storage.indexCacheTtl, TimeUnit.SECONDS)
        .<Entry<String, Long>, Pair>build().asMap();
      this.table = table;
      this.bucketCount = storage.bucketCount;
      Insert insertQuery = declarePartitionKey(QueryBuilder.insertInto(table)
        .value("ts", QueryBuilder.bindMarker("ts"))
        .value("trace_id", QueryBuilder.bindMarker("trace_id")));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
      timestampCodec = new TimestampCodec(session);
    }

    Indexer newIndexer() {
      return new RealIndexer(sharedState, table);
    }

    abstract Insert declarePartitionKey(Insert insert);

    abstract BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey);

    @Override protected IndexTraceId newCall(Input input) {
      return new IndexTraceId(this, input);
    }

    @Override public void clear() {
      super.clear();
      sharedState.clear();
    }
  }

  final Factory factory;
  final Input input;

  IndexTraceId(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.bindPartitionKey(
      factory.preparedStatement.bind()
        .setLong("trace_id", input.trace_id())
        .setBytesUnsafe("ts", factory.timestampCodec.serialize(input.ts())), input.partitionKey()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", factory.getClass().getSimpleName());
  }

  @Override public IndexTraceId clone() {
    return new IndexTraceId(factory, input);
  }

  static final class RealIndexer implements Indexer {
    final Map<Entry<String, Long>, Pair> sharedState;
    final String table;
    final Set<Input> inputs = new LinkedHashSet<>();

    RealIndexer(Map<Entry<String, Long>, Pair> sharedState, String table) {
      this.sharedState = sharedState;
      this.table = table;
    }

    @Override public void add(IndexTraceId.Input input) {
      inputs.add(input);
    }

    /**
     * The input may include inserts that already occurred, or are redundant as they don't impact
     * QueryRequest.endTs or QueryRequest.loopback. For example, a parsed timestamp could be between
     * timestamps of rows that already exist for a particular trace. Optimized results will be
     * smaller when the input includes traces with local spans, or when other threads indexed the
     * same trace.
     */
    @Override public Iterator<Input> iterator() {
      Set<IndexTraceId.Input> result = entriesThatIncreaseGap();
      if (inputs.size() > result.size() && LOG.isDebugEnabled()) {
        int delta = inputs.size() - result.size();
        LOG.debug("optimized out {}/{} inserts into {}", delta, inputs.size(), table);
      }
      return result.iterator();
    }

    Set<IndexTraceId.Input> entriesThatIncreaseGap() {
      if (inputs.size() <= 1) return inputs;
      Set<Entry<String, Long>> toUpdate = new LinkedHashSet<>();
      Map<Entry<String, Long>, Set<Long>> mappedInputs = new LinkedHashMap<>();

      // Enter a loop that affects shared state when an update widens the time interval for a key.
      for (IndexTraceId.Input input : inputs) {
        Entry<String, Long> key =
          new SimpleImmutableEntry<>(input.partitionKey(), input.trace_id());
        long timestamp = input.ts();
        add(mappedInputs, key, timestamp);
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

          long first = Math.min(timestamp, oldRange.left);
          long last = Math.max(timestamp, oldRange.right);

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
      Set<IndexTraceId.Input> result = new LinkedHashSet<>();
      for (Entry<String, Long> needsUpdate : toUpdate) {
        Pair firstLast = sharedState.get(needsUpdate);
        if (containsEntry(mappedInputs, needsUpdate, firstLast.left)) {
          result.add(Input.create(needsUpdate.getKey(), firstLast.left, needsUpdate.getValue()));
        }
        if (containsEntry(mappedInputs, needsUpdate, firstLast.right)) {
          result.add(Input.create(needsUpdate.getKey(), firstLast.right, needsUpdate.getValue()));
        }
      }
      return result;
    }

    static <K, V> void add(Map<K, Set<V>> multimap, K key, V value) {
      Set<V> valueContainer = multimap.get(key);
      if (valueContainer == null) {
        multimap.put(key, valueContainer = new LinkedHashSet<>());
      }
      valueContainer.add(value);
    }

    static <K, V> boolean containsEntry(Map<K, Set<V>> multimap, K key, V value) {
      Set<V> result = multimap.get(key);
      return result != null && result.contains(value);
    }
  }
}
