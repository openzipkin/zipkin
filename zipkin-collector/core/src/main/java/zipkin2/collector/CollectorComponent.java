/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.collector;

import java.util.List;
import zipkin2.Component;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

/**
 * The collector represents the server-side of a transport. Its job is to take spans from a
 * transport and store ones it has sampled.
 *
 * <p>Call {@link #start()} to start collecting spans.
 */
public abstract class CollectorComponent extends Component {

  /**
   * Starts the server-side of the transport, typically listening or looking up a queue.
   *
   * <p>Many implementations block the calling thread until services are available.
   */
  public abstract CollectorComponent start();

  public abstract static class Builder {
    /**
     * Once spans are sampled, they are {@link SpanConsumer#accept(List)} queued for storage} using
     * this component.
     */
    public abstract Builder storage(StorageComponent storage);

    /**
     * Aggregates and reports collection metrics to a monitoring system. Should be {@link
     * CollectorMetrics#forTransport(String) scoped to this transport}. Defaults to no-op.
     */
    public abstract Builder metrics(CollectorMetrics metrics);

    /**
     * {@link CollectorSampler#isSampled(String, boolean) samples spans} to reduce load on the
     * storage system. Defaults to always sample.
     */
    public abstract Builder sampler(CollectorSampler sampler);

    public abstract CollectorComponent build();
  }
}
