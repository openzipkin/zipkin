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

import java.util.ArrayList;
import java.util.List;

import static zipkin.internal.Util.checkNotNull;

final class InternalSamplingAsyncSpanConsumer implements AsyncSpanConsumer {
  final AsyncSpanConsumer asyncConsumer;
  final CollectorSampler sampler;
  final CollectorMetrics metrics;

  InternalSamplingAsyncSpanConsumer(AsyncSpanConsumer asyncConsumer, CollectorSampler sampler,
      CollectorMetrics metrics) {
    this.asyncConsumer = checkNotNull(asyncConsumer, "asyncConsumer");
    this.sampler = checkNotNull(sampler, "sampler");
    this.metrics = checkNotNull(metrics, "metrics");
  }

  @Override
  public void accept(List<Span> input, Callback<Void> callback) {
    List<Span> sampled = new ArrayList<>(input.size());
    for (Span s : input) {
      if (sampler.isSampled(s)) sampled.add(s);
    }
    int dropped = input.size() - sampled.size();
    if (dropped > 0) metrics.incrementSpansDropped(dropped);
    asyncConsumer.accept(sampled, callback);
  }
}
