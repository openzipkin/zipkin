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
package zipkin;

import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class InternalSamplingAsyncSpanConsumerTest {

  InMemorySpanStore store = new InMemorySpanStore();
  AsyncSpanConsumer consumer = StorageAdapters.blockingToAsync(store::accept, Runnable::run);
  Sampler never = Sampler.create(0f);

  Span.Builder builder = new Span.Builder()
      .traceId(1234L)
      .id(1235L)
      .parentId(1234L)
      .name("md5")
      .timestamp(System.currentTimeMillis() * 1000)
      .duration(150L)
      .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "digest",
          Endpoint.create("service", 127 << 24 | 1, 8080)));

  @Test
  public void debugFlagWins() {
    AsyncSpanConsumer samplingConsumer = new InternalSamplingAsyncSpanConsumer(consumer, never);

    samplingConsumer.accept(asList(builder.debug(true).build()), AsyncSpanConsumer.NOOP_CALLBACK);

    assertThat(store.getServiceNames()).containsExactly("service");
  }

  @Test
  public void unsampledSpansArentStored() {
    AsyncSpanConsumer samplingConsumer = new InternalSamplingAsyncSpanConsumer(consumer, never);

    samplingConsumer.accept(asList(builder.build()), AsyncSpanConsumer.NOOP_CALLBACK);

    assertThat(store.getServiceNames()).isEmpty();
  }
}
