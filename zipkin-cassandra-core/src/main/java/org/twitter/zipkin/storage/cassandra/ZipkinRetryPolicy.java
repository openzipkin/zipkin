
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.policies.RetryPolicy.RetryDecision;

public final class ZipkinRetryPolicy implements RetryPolicy {

    public static final ZipkinRetryPolicy INSTANCE = new ZipkinRetryPolicy();

    private ZipkinRetryPolicy() {}

    @Override
    public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
        return RetryDecision.retry(ConsistencyLevel.ONE);
    }

    @Override
    public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
        return RetryDecision.retry(ConsistencyLevel.ONE);
    }

    @Override
    public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
        return RetryDecision.retry(ConsistencyLevel.ONE);
    }
}
