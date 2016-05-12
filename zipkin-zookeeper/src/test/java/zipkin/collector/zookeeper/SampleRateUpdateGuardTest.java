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

import com.google.common.io.Closer;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.curator.test.InstanceSpec;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inspired by mesos chronos JobSchedulerElectionSpec
 *
 * <p>https://github.com/mesos/chronos/blob/master/src/test/scala/org/apache/mesos/chronos/scheduler/jobs/JobSchedulerElectionSpec.scala
 */
public class SampleRateUpdateGuardTest {
  @Rule public ZooKeeperRule zookeeper = new ZooKeeperRule();
  Closer closer = Closer.create();

  @After
  public void closeThings() throws IOException {
    closer.close();
  }

  @Test
  public void idempotentClose() throws Exception {
    SampleRateUpdateGuard guard = guard(new ZooKeeperCollectorSampler.Builder());
    guard.close();
    guard.close();
  }

  @Test
  public void passesWhenALeader() throws Exception {
    SampleRateUpdateGuard guard1 = guard(new ZooKeeperCollectorSampler.Builder());
    SampleRateUpdateGuard guard2 = guard(new ZooKeeperCollectorSampler.Builder());

    waitForALeader(guard1, guard2);

    SampleRateUpdateGuard leader = guard1.latch.hasLeadership() ? guard1 : guard2;
    SampleRateUpdateGuard follower = guard1 != leader ? guard1 : guard2;

    assertThat(leader.get()).isTrue();

    assertThat(follower.get()).isFalse();
  }

  @Test
  public void onlyPassesOncePerInterval() throws Exception {
    SampleRateUpdateGuard guard1 = guard(new ZooKeeperCollectorSampler.Builder().updateFrequency(1));
    SampleRateUpdateGuard guard2 = guard(new ZooKeeperCollectorSampler.Builder().updateFrequency(1));

    waitForALeader(guard1, guard2);

    // should pass immediately
    SampleRateUpdateGuard leader = guard1.latch.hasLeadership() ? guard1 : guard2;

    int updates = 0;
    long start = System.nanoTime();
    // should pass only once, as the interval is 1 second, and we only loop for 1.5s
    while (System.nanoTime() < start + TimeUnit.MILLISECONDS.toNanos(1500)) {
      if (leader.get()) updates++;
      Thread.sleep(10);
    }

    assertThat(updates).isEqualTo(2);
  }

  @Test
  public void shouldElectOnlyOneLeader() throws Exception {
    SampleRateUpdateGuard guard1 = guard(new ZooKeeperCollectorSampler.Builder());
    SampleRateUpdateGuard guard2 = guard(new ZooKeeperCollectorSampler.Builder());

    waitForALeader(guard1, guard2);

    assertThat(guard1.latch.hasLeadership() ^ guard2.latch.hasLeadership())
        .withFailMessage("One guard, but not both, should be the leader")
        .isTrue();
  }

  @Test
  public void shouldStillBeOneLeaderAfterZKFailure() throws Exception {
    SampleRateUpdateGuard guard1 = guard(new ZooKeeperCollectorSampler.Builder());
    SampleRateUpdateGuard guard2 = guard(new ZooKeeperCollectorSampler.Builder());

    waitForALeader(guard1, guard2);

    SampleRateUpdateGuard leader = guard1.latch.hasLeadership() ? guard1 : guard2;

    InstanceSpec instance = zookeeper.cluster.findConnectionInstance(
        leader.client.getZookeeperClient().getZooKeeper());
    zookeeper.cluster.killServer(instance);

    waitForALeader(guard1, guard2);

    assertThat(guard1.latch.hasLeadership() ^ guard2.latch.hasLeadership())
        .withFailMessage("After a ZK node failure, one guard, but not both, should be the leader")
        .isTrue();
  }

  @Test
  public void electsNewLeaderOnClose() throws Exception {
    SampleRateUpdateGuard guard1 = guard(new ZooKeeperCollectorSampler.Builder());
    SampleRateUpdateGuard guard2 = guard(new ZooKeeperCollectorSampler.Builder());

    waitForALeader(guard1, guard2);

    SampleRateUpdateGuard leader = guard1.latch.hasLeadership() ? guard1 : guard2;
    SampleRateUpdateGuard follower = guard1 != leader ? guard1 : guard2;

    leader.close();

    waitForALeader(guard1, guard2);

    assertThat(leader.latch.hasLeadership())
        .withFailMessage("Leader latch should lose leadership on close")
        .isFalse();

    assertThat(follower.latch.hasLeadership())
        .withFailMessage("Former follower should be the leader on master failure")
        .isTrue();
  }

  int count = 0;

  SampleRateUpdateGuard guard(ZooKeeperCollectorSampler.Builder builder) {
    builder.id("guard-" + count++);
    return closer.register(new SampleRateUpdateGuard(zookeeper.client, builder));
  }

  void waitForALeader(SampleRateUpdateGuard... guards) throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      for (SampleRateUpdateGuard guard : guards) {
        if (guard.latch.hasLeadership()) return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("No guards became a leader");
  }
}
