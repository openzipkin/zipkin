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

package zipkin.storage.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;

/** This class was copied from org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy */
final class ZipkinRetryPolicy implements RetryPolicy {

  public static final ZipkinRetryPolicy INSTANCE = new ZipkinRetryPolicy();

  private ZipkinRetryPolicy() {
  }

  @Override
  public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl,
      int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
    return RetryDecision.retry(ConsistencyLevel.ONE);
  }

  @Override
  public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType,
      int requiredAcks, int receivedAcks, int nbRetry) {
    return RetryDecision.retry(ConsistencyLevel.ONE);
  }

  @Override
  public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica,
      int aliveReplica, int nbRetry) {
    return RetryDecision.retry(ConsistencyLevel.ONE);
  }

  @Override
  public RetryDecision onRequestError(Statement statement, ConsistencyLevel cl,
      DriverException e, int nbRetry) {
    return RetryDecision.tryNextHost(ConsistencyLevel.ONE);
  }

  @Override
  public void init(Cluster cluster) {
    // nothing to do
  }

  @Override
  public void close() {
    // nothing to do
  }
}
