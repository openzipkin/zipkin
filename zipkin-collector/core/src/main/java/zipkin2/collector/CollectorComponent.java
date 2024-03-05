/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
