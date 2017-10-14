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
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkNotNull;

/**
 * This reduces load on cassandra by preventing semantically equivalent requests from being invoked,
 * subject to a local TTL.
 *
 * <p>Ex. If you want to test that you don't repeatedly send bad data, you could send a 400 back.
 *
 * <pre>{@code
 * ttl = 60 * 1000; // 1 minute
 * deduper = new DeduplicatingExecutor(session, ttl);
 *
 * // the result of the first execution against "foo" is returned to other callers
 * // until it expires a minute later.
 * deduper.maybeExecute(bound, "foo");
 * deduper.maybeExecute(bound, "foo");
 * }</pre>
 */
class DeduplicatingExecutor { // not final for testing

  private final Session session;
  private final LoadingCache<BoundStatementKey, ListenableFuture<Void>> cache;

  /**
   * @param session which conditionally executes bound statements
   * @param ttl how long the results of statements are remembered, in milliseconds.
   */
  DeduplicatingExecutor(Session session, long ttl) {
    this.session = session;
    this.cache = CacheBuilder.newBuilder()
        .expireAfterWrite(ttl, TimeUnit.MILLISECONDS)
        .ticker(new Ticker() {
          @Override public long read() {
            return nanoTime();
          }
        })
        // TODO: maximum size or weight
        .build(new CacheLoader<BoundStatementKey, ListenableFuture<Void>>() {
          @Override public ListenableFuture<Void> load(final BoundStatementKey key) {
            ListenableFuture<?> cassandraFuture = executeAsync(key.statement);

            // Drop the cassandra future so that we don't hold references to cassandra state for
            // long periods of time.
            final SettableFuture<Void> disconnectedFuture = SettableFuture.create();
            Futures.addCallback(cassandraFuture, new FutureCallback<Object>() {

              @Override public void onSuccess(@Nullable Object result) {
                disconnectedFuture.set(null);
              }

              @Override public void onFailure(Throwable t) {
                cache.invalidate(key);
                disconnectedFuture.setException(t);
              }
            });
            return disconnectedFuture;
          }
        });
  }

  /**
   * Upon success, the statement's result will be remembered and returned for all subsequent
   * executions with the same key, subject to a local TTL.
   *
   * <p>The results of failed statements are forgotten based on the supplied key.
   *
   * @param statement what to conditionally execute
   * @param key determines equivalence of the bound statement
   * @return future of work initiated by this or a previous request
   */
  ListenableFuture<Void> maybeExecuteAsync(BoundStatement statement, Object key) {
    BoundStatementKey cacheKey = new BoundStatementKey(statement, key);
    try {
      ListenableFuture<Void> result = cache.get(new BoundStatementKey(statement, key));
      // A future could be constructed directly (i.e. immediate future), get the value to
      // see if it was exceptional. If so, the catch block will invalidate that key.
      if (result.isDone()) result.get();
      return result;
    } catch (UncheckedExecutionException | ExecutionException e) {
      cache.invalidate(cacheKey);
      return Futures.immediateFailedFuture(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError();
    }
  }

  // visible for testing, since nanoTime is weird and can return negative
  long nanoTime() {
    return System.nanoTime();
  }

  @VisibleForTesting ListenableFuture<?> executeAsync(BoundStatement statement) {
    return session.executeAsync(statement);
  }

  @VisibleForTesting void clear() {
    cache.invalidateAll();
  }

  /** Used to hold a reference to the last statement executed, but without using it in hashCode */
  static final class BoundStatementKey {
    final BoundStatement statement;
    final Object key;

    BoundStatementKey(BoundStatement statement, Object key) {
      this.statement = checkNotNull(statement, "statement");
      this.key = checkNotNull(key, "key");
    }

    @Override
    public String toString() {
      return "(" + key + ", " + statement + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (o instanceof BoundStatementKey) {
        return this.key.equals(((BoundStatementKey) o).key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }
  }
}
