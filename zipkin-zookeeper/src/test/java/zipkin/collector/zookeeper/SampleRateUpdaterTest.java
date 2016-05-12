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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.curator.framework.recipes.nodes.GroupMember;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class SampleRateUpdaterTest {
  static final String PREFIX = "/" + SampleRateUpdaterTest.class.getSimpleName();
  static final String storeRatePath = PREFIX + "/storeRates";

  @Rule public ZooKeeperRule zookeeper = new ZooKeeperRule();

  GroupMember local;
  GroupMember peer;

  @Test public void readsAllChildren() throws Exception {
    Map<String, Integer> inputs = new ConcurrentHashMap<>();

    Function<Map<String, Integer>, Optional<Float>> calculator = (input) -> {
      inputs.putAll(input);
      return Optional.empty();
    };

    updater(calculator, () -> Boolean.TRUE);

    local.setThisData("100".getBytes());
    peer.setThisData("200".getBytes());
    zookeeper.sleepABit();

    assertThat(inputs).containsOnly(
        entry("zipkin-server@1.1.1.1:8080", 100),
        entry("zipkin-server@2.2.2.2:8080", 200)
    );
  }

  /**
   * If a collector node goes down, or is renamed, we shouldn't read the old key, as it would skew
   * the sample rate.
   */
  @Test public void onlyReadsCurrentGroupMembers() throws Exception {
    Map<String, Integer> inputs = new ConcurrentHashMap<>();

    Function<Map<String, Integer>, Optional<Float>> calculator = (input) -> {
      inputs.putAll(input);
      return Optional.empty();
    };

    updater(calculator, () -> Boolean.TRUE);

    local.setThisData("100".getBytes());
    peer.setThisData("200".getBytes());
    zookeeper.sleepABit();

    assertThat(inputs).containsOnly(
        entry("zipkin-server@1.1.1.1:8080", 100),
        entry("zipkin-server@2.2.2.2:8080", 200)
    );
    inputs.clear();

    // Simulate a peer going down and the local node taking over the load
    peer.close();
    local.setThisData("300".getBytes());
    zookeeper.sleepABit();

    assertThat(inputs).containsOnly(
        entry("zipkin-server@1.1.1.1:8080", 300)
    );
  }

  @Test public void skipsMalformedData() throws Exception {
    Map<String, Integer> inputs = new ConcurrentHashMap<>();

    Function<Map<String, Integer>, Optional<Float>> calculator = (input) -> {
      inputs.putAll(input);
      return Optional.empty();
    };

    updater(calculator, () -> Boolean.TRUE);

    local.setThisData("crying babies".getBytes());
    peer.setThisData("200".getBytes());
    zookeeper.sleepABit();

    assertThat(inputs).containsOnly(
        entry("zipkin-server@2.2.2.2:8080", 200)
    );
  }

  @Test public void writesSampleRate() throws Exception {
    updater((input) -> Optional.of(0.9f), () -> Boolean.TRUE);

    local.setThisData("100".getBytes()); // trigger
    zookeeper.sleepABit();

    assertThat(zookeeper.client.getData().forPath(PREFIX + "/sampleRate"))
        .isEqualTo("0.9".getBytes());
  }

  @Test public void obeysGuard() throws Exception {
    updater((input) -> Optional.of(0.9f), () -> Boolean.FALSE);

    local.setThisData("100".getBytes());
    zookeeper.sleepABit();

    assertThat(zookeeper.client.checkExists().forPath(PREFIX + "/sampleRate"))
        .isNull();
  }

  Closer closer = Closer.create();

  SampleRateUpdater updater(Function<Map<String, Integer>, Optional<Float>> calculator,
      Supplier<Boolean> guard) {
    return closer.register(new SampleRateUpdater(
        zookeeper.client, local, storeRatePath, PREFIX + "/sampleRate", calculator, guard));
  }

  @Before
  public void joinGroup() throws Exception {
    zookeeper.client.createContainers(storeRatePath);
    local = closer.register(
        new GroupMember(zookeeper.client, storeRatePath, "zipkin-server@1.1.1.1:8080"));
    peer = closer.register(
        new GroupMember(zookeeper.client, storeRatePath, "zipkin-server@2.2.2.2:8080"));
    local.start();
    peer.start();
    zookeeper.sleepABit(); // for the group members to start
  }

  @After
  public void closeThings() throws IOException {
    closer.close();
  }
}
