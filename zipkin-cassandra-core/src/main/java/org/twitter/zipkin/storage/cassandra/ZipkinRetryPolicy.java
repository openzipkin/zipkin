
package org.twitter.zipkin.storage.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;

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
