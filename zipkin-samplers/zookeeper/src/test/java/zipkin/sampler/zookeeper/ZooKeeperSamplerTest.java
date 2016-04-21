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

import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.InMemoryStorage;
import zipkin.Span;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static org.apache.curator.framework.CuratorFrameworkFactory.newClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

public class ZooKeeperSamplerTest {
  InMemoryStorage storage = new InMemoryStorage();

  @Test public void sampleRateReadFromZookeeper() throws Exception {
    // Simulates an existing sample rate, set from zookeeper
    client.setData().forPath("/sampleRate", "0.9".getBytes());

    accept(spans);

    assertThat(storage.acceptedSpanCount())
        .isCloseTo((int) (spans.length * 0.9), withinPercentage(3));
  }

  @Test public void ignoresBadRateReadFromZookeeper() throws Exception {
    // Simulates a bad rate, set from zookeeper
    client.setData().forPath("/sampleRate", "1.9".getBytes());

    accept(spans);

    assertThat(storage.acceptedSpanCount())
        .isEqualTo(spans.length); // default is retain all
  }

  @Test public void exportsStoreRateToZookeeperOnInterval() throws Exception {
    accept(spans);

    // Until the update interval, we'll see a store rate of zero
    assertThat(getLocalStoreRate()).isZero();

    // Await until update interval passes (1 second + fudge)
    Thread.sleep(1000); // let the update interval pass

    // since update frequency is secondly, the rate exported to ZK will be the amount stored * 60
    assertThat(getLocalStoreRate())
        .isEqualTo(spans.length * 60);
  }

  @Before public void clear() throws Exception {
    // default to always sample
    client.setData().forPath("/sampleRate", "1.0".getBytes());

    // remove any storage rate members
    List<String> groupMembers = client.getChildren().forPath("/storeRates");
    if (!groupMembers.isEmpty()) {
      client.setData().forPath("/storeRates/" + groupMembers.get(0), new byte[] {'0'});
    }
  }

  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  static Span[] spans = new Random().longs(100000).mapToObj(t -> span(t)).toArray(Span[]::new);

  static Span span(long traceId) {
    Endpoint e = Endpoint.create("service", 127 << 24 | 1, 8080);
    Annotation ann = Annotation.create(System.currentTimeMillis() * 1000, Constants.SERVER_RECV, e);
    return new Span.Builder().traceId(traceId).id(traceId).name("get").addAnnotation(ann).build();
  }

  static CuratorFramework client;
  static TestingServer zookeeper;
  static ZooKeeperSampler sampler;

  /** Blocks until the callback completes to allow read-your-writes consistency during tests. */
  void accept(Span... spans) {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    storage.asyncSpanConsumer(sampler).accept(asList(spans), captor);
    captor.get(); // block on result
  }

  @BeforeClass public static void beforeAll() throws Exception {
    zookeeper = new TestingServer();
    client = newClient(zookeeper.getConnectString(), new RetryOneTime(200 /* ms */));
    zookeeper.start();
    client.start();
    // ZooKeeperSampler doesn't create these!
    client.createContainers("/election");
    client.createContainers("/storeRates");
    client.createContainers("/sampleRate");
    client.createContainers("/targetStoreRate");

    sampler = new ZooKeeperSampler.Builder()
        .zookeeper(zookeeper.getConnectString())
        .basePath("") // shorten for test readability
        .updateFrequency(1) // least possible value
        .build();

    // prime zookeeper data, to make sure connection-concerns don't fail tests
    sampler.isSampled(spans[0]);
    Thread.sleep(1000); // let the update interval pass
  }

  @AfterClass public static void afterAll() throws IOException {
    client.close();
    zookeeper.close();
    sampler.close();
  }

  /** Twitter's zookeeper group is where you store the same value as a child node */
  static int getLocalStoreRate() throws Exception {
    String groupMember = client.getChildren().forPath("/storeRates").get(0);
    byte[] data = client.getData().forPath("/storeRates/" + groupMember);
    return data.length == 0 ? 0 : Integer.parseInt(new String(data));
  }
}


