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

import com.twitter.zipkin.sampler.AdaptiveSampleRate;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import zipkin.Sampler;
import zipkin.Span;

import static zipkin.internal.Util.checkArgument;
import static zipkin.internal.Util.checkNotNull;

public final class ZooKeeperSampler extends Sampler implements AutoCloseable {

  /** Configuration including defaults needed to consume spans from a Kafka basePath. */
  public static final class Builder {
    float initialRate = 1.0f;
    String basePath = "/zipkin/sampler/";
    String zookeeper = "localhost:2181";
    int updateFrequency = 30;
    int windowSize = 30 * 60;
    int sufficientWindowSize = 10 * 60;
    int outlierThreshold = 5 * 60;

    /** Rate used until an adaptive one calculated. 0.0001 means 0.01% of traces. Defaults to 1.0 */
    public Builder initialRate(float rate) {
      checkArgument(rate >= 0 && rate <= 1, "rate should be between 0 and 1: was %s", rate);
      this.initialRate = rate;
      return this;
    }

    /** Base path in ZooKeeper for the sampler to use. Defaults to "zipkin" */
    public Builder basePath(String basePath) {
      this.basePath = checkNotNull(basePath, "basePath");
      return this;
    }

    /** The zookeeper connect string, Defaults to 127.0.0.1:2181 */
    public Builder zookeeper(String zookeeper) {
      this.zookeeper = checkNotNull(zookeeper, "zookeeper");
      return this;
    }

    /** Frequency in seconds which to update the sample rate. Defaults to 30 */
    public Builder updateFrequency(int updateFrequency) {
      checkArgument(updateFrequency >= 1, "updateFrequency must be at least 1 second");
      this.updateFrequency = updateFrequency;
      return this;
    }

    /** Seconds of request rate data to base sample rate on. Defaults to 1800 (30 minutes) */
    public Builder windowSize(int windowSize) {
      this.windowSize = windowSize;
      return this;
    }

    /**
     * Seconds of request rate data to gather before calculating sample rate. Defaults to 600 (10
     * minutes)
     */
    public Builder sufficientWindowSize(int sufficientWindowSize) {
      this.sufficientWindowSize = sufficientWindowSize;
      return this;
    }

    /** Seconds to see outliers before updating sample rate. Defaults to 300 (5 minutes) */
    public Builder outlierThreshold(int outlierThreshold) {
      this.outlierThreshold = outlierThreshold;
      return this;
    }

    public ZooKeeperSampler build() {
      return new ZooKeeperSampler(this);
    }
  }

  final AtomicLong boundary;
  final AtomicInteger spanCount;
  final AdaptiveSampleRate sampleRate;

  ZooKeeperSampler(Builder builder) {
    this.boundary = new AtomicLong((long) (Long.MAX_VALUE * builder.initialRate)); // safe cast as less <= 1
    this.spanCount = new AtomicInteger();
    this.sampleRate = new AdaptiveSampleRate(
        boundary,
        spanCount,
        builder.zookeeper,
        Collections.emptyMap(),
        builder.basePath,
        builder.updateFrequency,
        builder.windowSize,
        builder.sufficientWindowSize,
        builder.outlierThreshold
    );
  }

  @Override
  public void close() {
    this.sampleRate.close();
  }

  @Override public boolean isSampled(Span span) {
    boolean result = super.isSampled(span);
    if (result) spanCount.incrementAndGet();
    return result;
  }

  @Override protected long boundary() {
    return boundary.get();
  }
}
