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

package zipkin2.storage.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.RetryPolicy;

/** This class was copied from org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy */
final class ZipkinRetryPolicy implements RetryPolicy {

  public static final ZipkinRetryPolicy INSTANCE = new ZipkinRetryPolicy();

  private ZipkinRetryPolicy() {
  }

  @Override
  public RetryDecision onReadTimeout(Statement stmt, ConsistencyLevel cl, int required,
    int received, boolean retrieved, int retry) {

    if (retry > 1) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException expected) {
      }
    }
    return stmt.isIdempotent()
      ? retry < 10 ? RetryDecision.retry(cl) : RetryDecision.rethrow()
      : DefaultRetryPolicy.INSTANCE.onReadTimeout(stmt, cl, required, received, retrieved, retry);
  }

  @Override
  public RetryDecision onWriteTimeout(Statement stmt, ConsistencyLevel cl, WriteType type,
    int required, int received, int retry) {

    return stmt.isIdempotent()
      ? RetryDecision.retry(cl)
      : DefaultRetryPolicy.INSTANCE.onWriteTimeout(stmt, cl, type, required, received, retry);
  }

  @Override
  public RetryDecision onUnavailable(Statement stmt, ConsistencyLevel cl, int required,
    int aliveReplica, int retry) {
    return DefaultRetryPolicy.INSTANCE.onUnavailable(stmt, cl, required, aliveReplica,
      retry == 1 ? 0 : retry);
  }

  @Override
  public RetryDecision onRequestError(Statement stmt, ConsistencyLevel cl, DriverException ex,
    int nbRetry) {
    return DefaultRetryPolicy.INSTANCE.onRequestError(stmt, cl, ex, nbRetry);
  }

  @Override
  public void init(Cluster cluster) {
  }

  @Override
  public void close() {
  }
}
