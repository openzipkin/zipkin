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
package zipkin.collector.zookeeper;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.collector.zookeeper.ZooKeeperCollectorSampler.Builder;

/**
 * Allows an update while a leader, but not more often than {@link Builder#updateFrequency(int)}
 * seconds.
 */
final class SampleRateUpdateGuard implements Supplier<Boolean>, Closeable {
  final Logger log = LoggerFactory.getLogger(SampleRateUpdateGuard.class);

  final CuratorFramework client; // visible for testing
  final LeaderLatch latch; // visible for testing
  private final long updateFrequencyNanos;
  private long nextRun = System.nanoTime(); // guarded by this

  SampleRateUpdateGuard(CuratorFramework client, Builder builder) {
    this.client = client;
    this.updateFrequencyNanos = TimeUnit.SECONDS.toNanos(builder.updateFrequency);
    String electionPath = builder.basePath + "/election";
    try {
      client.checkExists().creatingParentContainersIfNeeded().forPath(electionPath);
    } catch (Exception e) {
      throw new IllegalStateException("Error creating " + electionPath, e);
    }
    latch = new LeaderLatch(client, electionPath, builder.id);
    log.debug(builder.id + " is trying to become the leader");
    try {
      latch.start();
    } catch (Exception e) {
      throw new IllegalStateException("Error starting latch for " + electionPath, e);
    }
  }

  @Override public Boolean get() {
    if (!latch.hasLeadership()) return false;

    synchronized (this) {
      if (System.nanoTime() < nextRun) {
        return false;
      } else {
        nextRun = System.nanoTime() + updateFrequencyNanos;
        return true;
      }
    }
  }

  @Override public void close() throws IOException {
    try {
      latch.close();
    } catch (IllegalStateException ignored) {
      // Already closed or has not been started isn't worth raising an exception over.
    }
  }
}
