/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import static org.junit.Assert.assertSame;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import static org.mockito.Mockito.mock;
import zipkin2.storage.StorageComponent;

public class ThrottledStorageComponentTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void spanConsumer_isProxied() {
    StorageComponent delegate = mock(StorageComponent.class);
    MeterRegistry registry = new CompositeMeterRegistry();

    ThrottledStorageComponent throttle = new ThrottledStorageComponent(delegate, registry, 1, 2, 1);

    assertSame(ThrottledSpanConsumer.class, throttle.spanConsumer().getClass());
  }

  @Test
  public void createComponent_withoutMeter() {
    StorageComponent delegate = mock(StorageComponent.class);

    new ThrottledStorageComponent(delegate, null, 1, 2, 1);
    // no exception == pass
  }

  @Test
  public void createComponent_withZeroSizedQueue() {
    StorageComponent delegate = mock(StorageComponent.class);
    MeterRegistry registry = new CompositeMeterRegistry();

    int queueSize = 0;
    new ThrottledStorageComponent(delegate, registry, 1, 2, queueSize);
    // no exception == pass
  }

  @Test
  public void createComponent_withNegativeQueue() {
    StorageComponent delegate = mock(StorageComponent.class);
    MeterRegistry registry = new CompositeMeterRegistry();

    expectedException.expect(IllegalArgumentException.class);
    int queueSize = -1;
    new ThrottledStorageComponent(delegate, registry, 1, 2, queueSize);
  }
}
