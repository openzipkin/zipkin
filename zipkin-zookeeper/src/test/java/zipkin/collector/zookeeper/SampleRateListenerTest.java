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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SampleRateListenerTest {
  static final String PREFIX = "/" + SampleRateListenerTest.class.getSimpleName();

  @Rule public ZooKeeperRule zookeeper = new ZooKeeperRule();

  AtomicReference<Float> sampleRate = new AtomicReference<>(1.0f);
  AtomicLong boundary = new AtomicLong(Long.MAX_VALUE);
  SampleRateListener listener;

  @Before
  public void openThings() throws IOException {
    listener =
        new SampleRateListener(zookeeper.client, PREFIX + "/sampleRate", sampleRate, boundary);
  }

  @After
  public void closeThings() throws IOException {
    listener.close();
  }

  @Test
  public void idempotentClose() throws Exception {
    listener.close();
    listener.close();
  }

  @Test public void ignoresBadRateReadFromZookeeper() throws Exception {
    // Simulates a bad rate, set from connectString
    zookeeper.create(PREFIX + "/sampleRate", "1.9");

    assertThat(listener.sampleRate.get())
        .isEqualTo(1.0f); // didn't change
    assertThat(listener.boundary.get())
        .isEqualTo(Long.MAX_VALUE); // didn't change
  }

  @Test
  public void setsRateAndBoundary() throws Exception {
    zookeeper.create(PREFIX + "/sampleRate", "0.9");

    assertThat(listener.sampleRate.get())
        .isEqualTo(0.9f);
    assertThat(listener.boundary.get())
        .isEqualTo((long) (Long.MAX_VALUE * 0.9f));
  }
}
