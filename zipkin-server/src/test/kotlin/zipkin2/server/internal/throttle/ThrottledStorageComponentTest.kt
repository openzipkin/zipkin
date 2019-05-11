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
package zipkin2.server.internal.throttle

import com.linecorp.armeria.common.metric.NoopMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import zipkin2.storage.InMemoryStorage

class ThrottledStorageComponentTest {
  val delegate = InMemoryStorage.newBuilder().build()
  val registry = NoopMeterRegistry.get()

  @Test fun spanConsumer_isProxied() {
    val throttle = ThrottledStorageComponent(delegate, registry, 1, 2, 1)

    assertThat(throttle.spanConsumer().accept(listOf()))
      .isInstanceOf(ThrottledCall::class.java)
  }

  @Test fun createComponent_withZeroSizedQueue() {
    val queueSize = 0
    ThrottledStorageComponent(delegate, registry, 1, 2, queueSize)
    // no exception == pass
  }

  @Test(expected = IllegalArgumentException::class)
  fun createComponent_withNegativeQueue() {
    val queueSize = -1
    ThrottledStorageComponent(delegate, registry, 1, 2, queueSize)
  }

  @Test fun niceToString() {
    assertThat(ThrottledStorageComponent(delegate, registry, 1, 2, 1))
      .hasToString("Throttled(InMemoryStorage{traceCount=0})");
  }
}
