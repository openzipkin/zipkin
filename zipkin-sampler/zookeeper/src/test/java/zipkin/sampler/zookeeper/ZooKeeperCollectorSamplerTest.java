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
package zipkin.sampler.zookeeper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin.CollectorMetrics;
import zipkin.InMemoryStorage;
import zipkin.Span;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static zipkin.TestObjects.LOTS_OF_SPANS;

public class ZooKeeperCollectorSamplerTest {
  static final String PREFIX = "/" + ZooKeeperCollectorSamplerTest.class.getSimpleName();
  @Rule public ZooKeeperRule zookeeper = new ZooKeeperRule();

  InMemoryStorage storage = new InMemoryStorage();
  ZooKeeperCollectorSampler sampler;
  CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;

  @Before public void clear() throws Exception {
    if (sampler != null) {
      sampler.close();
    }
    sampler = new ZooKeeperCollectorSampler.Builder()
        .basePath(PREFIX)
        .updateFrequency(1) // least possible value
        .build(zookeeper.client);
  }

  @Test public void sampleRateReadFromZookeeper() throws Exception {
    // Simulates an existing sample rate, set from connectString
    zookeeper.create(PREFIX + "/sampleRate", "0.9");

    accept(LOTS_OF_SPANS);

    assertThat(storage.acceptedSpanCount())
        .isCloseTo((int) (LOTS_OF_SPANS.length * 0.9), withinPercentage(3));
  }

  @Test public void exportsStoreRateToZookeeperOnInterval() throws Exception {
    accept(LOTS_OF_SPANS);

    // Until the update interval, we'll see a store rate of zero
    assertThat(sampler.storeRate.get()).isZero();

    // Await until update interval passes (1 second + fudge)
    Thread.sleep(1000); // let the update interval pass

    // since update frequency is secondly, the rate exported to ZK will be the amount stored * 60
    assertThat(sampler.storeRate.get())
        .isEqualTo(LOTS_OF_SPANS.length * 60);
    assertThat(storeRateFromZooKeeper(sampler.groupMember))
        .isEqualTo(sampler.storeRate.get());
  }

  /** Blocks until the callback completes to allow read-your-writes consistency during tests. */
  void accept(Span... spans) {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    storage.asyncSpanConsumer(sampler, metrics).accept(asList(spans), captor);
    captor.get(); // block on result
  }

  int storeRateFromZooKeeper(String id) throws Exception {
    byte[] data = zookeeper.client.getData().forPath(PREFIX + "/storeRates/" + id);
    return data.length == 0 ? 0 : Integer.parseInt(new String(data));
  }
}


