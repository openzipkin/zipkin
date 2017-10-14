/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import com.google.common.io.Closer;
import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.nodes.GroupMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Span;
import zipkin.collector.CollectorSampler;
import zipkin.internal.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkArgument;
import static zipkin.internal.Util.checkNotNull;

/**
 * This is an adaptive sampler which can help prevent a surge in traffic from overwhelming the
 * zipkin storage layer. It works by coordinating a sample rate based on multiple instances vs a
 * target storage rate in spans/minute.
 *
 * <p>This assumes that each instance is storing every span it {@link #isSampled(long, Boolean)
 * samples}, and that the store rate is a useful metric (ex spans have relatively the same size and
 * depth.
 *
 * <p>If the storage layer is capable of 10k spans/minute, you'd set the target rate in ZooKeeper to
 * 10000. With this in mind, 10 balanced collectors writing 10k spans/minute would eventually see a
 * sample rate of 0.10, slowing them down to match what the storage is capable of.
 *
 * <h3>Implementation notes</h3>
 *
 * <p>This object spawns a single scheduling thread that reports its rate of {@link #isSampled(long,
 * Boolean) spans sampled}, per the {@link Builder#updateFrequency(int) update frequency}.
 *
 * <p>When a leader, this object summarizes recent sample rates and compares them against a target.
 *
 * <p>Algorithms and defaults are tuned to favor decreasing the sample rate vs increasing it. For
 * example, a surge in writes will fire a rate adjustment faster than a drop in writes.
 */
public final class ZooKeeperCollectorSampler extends CollectorSampler implements Closeable {
  final static Logger log = LoggerFactory.getLogger(ZooKeeperCollectorSampler.class);

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    float initialRate = 1.0f;
    String basePath = "/zipkin/sampler";
    String id = UUID.randomUUID().toString();
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

    /**
     * Stable name to use for this node in ZooKeeper groups and elections, ex. "cluster@host:port".
     * Defaults to a UUID.
     */
    public Builder id(String id) {
      this.id = checkNotNull(id, "id");
      return this;
    }

    /** Base path in ZooKeeper for the sampler to use. Defaults to "zipkin" */
    public Builder basePath(String basePath) {
      this.basePath = checkNotNull(basePath, "basePath");
      return this;
    }

    /** Frequency in seconds which to update the store and sample rate. Defaults to 30 */
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

    /**
     * @param client must be started, and will not be closed on {@link #close()}
     */
    public ZooKeeperCollectorSampler build(CuratorFramework client) {
      checkState(checkNotNull(client, "client").getState() == CuratorFrameworkState.STARTED,
          "%s is not started", client.getState());
      return new ZooKeeperCollectorSampler(this, client);
    }

    Builder() {
    }
  }

  final String groupMember;
  final AtomicLong boundary;
  final AtomicInteger spanCount;
  final AtomicInteger storeRate;
  final Closer closer = Closer.create();

  ZooKeeperCollectorSampler(Builder builder, CuratorFramework client) {
    groupMember = builder.id;
    boundary =
        new AtomicLong((long) (Long.MAX_VALUE * builder.initialRate)); // safe cast as less <= 1
    spanCount = new AtomicInteger(0);
    storeRate = new AtomicInteger();
    GroupMember storeRateMember = storeRateGroup(client, builder, closer, spanCount, storeRate);
    AtomicInteger targetStoreRate = targetStoreRate(client, builder, closer);
    AtomicReference<Float> sampleRate = new AtomicReference(builder.initialRate);
    String sampleRatePath = builder.basePath + "/sampleRate";
    closer.register(
        new SampleRateListener(client, sampleRatePath, sampleRate, boundary));
    closer.register(new SampleRateUpdater(
        client,
        storeRateMember,
        builder.basePath + "/storeRates",
        sampleRatePath,
        new SampleRateCalculatorInput(builder, targetStoreRate).andThen(
            new SampleRateCalculator(targetStoreRate, sampleRate)),
        closer.register(new SampleRateUpdateGuard(client, builder))));
  }

  static GroupMember storeRateGroup(CuratorFramework client, Builder builder, Closer closer,
      AtomicInteger spanCount, AtomicInteger storeRate) {
    String storeRatePath = ensureExists(client, builder.basePath + "/storeRates");

    GroupMember storeRateGroup =
        closer.register(new GroupMember(client, storeRatePath, builder.id));

    log.debug("{} is to join the group {}", builder.id, storeRatePath);
    storeRateGroup.start();

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    closer.register(executor::shutdown);

    ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
      int oldValue = storeRate.get();
      int newValue = (int) (1.0 * spanCount.getAndSet(0) * 60 / builder.updateFrequency);
      log.debug("Store rates was: {} now {}", oldValue, newValue);
      if (oldValue != newValue) {
        storeRate.set(newValue);
        storeRateGroup.setThisData(Integer.valueOf(newValue).toString().getBytes(UTF_8));
      }
    }, 0, builder.updateFrequency, TimeUnit.SECONDS);

    closer.register(() -> future.cancel(true));
    return storeRateGroup;
  }

  /** read-only */
  static AtomicInteger targetStoreRate(CuratorFramework client, Builder builder, Closer closer) {
    String targetStoreRatePath = ensureExists(client, builder.basePath + "/targetStoreRate");
    NodeCache cache = closer.register(new NodeCache(client, targetStoreRatePath));
    try {
      cache.start();
    } catch (Exception e) {
      throw new IllegalStateException("Error starting cache for " + targetStoreRatePath, e);
    }

    AtomicInteger targetStoreRate = new AtomicInteger();
    cache.getListenable().addListener(() -> {
      byte[] bytes = cache.getCurrentData().getData();
      if (bytes.length == 0) return;
      try {
        targetStoreRate.set(Integer.valueOf(new String(bytes, UTF_8)));
      } catch (NumberFormatException e) {
        log.warn("Error parsing target store rate {}", e.getMessage());
        return;
      }
    });
    return targetStoreRate;
  }

  static String ensureExists(CuratorFramework client, String path) {
    try {
      client.checkExists().creatingParentContainersIfNeeded().forPath(path);
      return path;
    } catch (Exception e) {
      throw new IllegalStateException("Error creating " + path, e);
    }
  }

  @Override
  public void close() throws IOException {
    closer.close();
  }

  @Override @Deprecated public boolean isSampled(Span span) {
    return isSampled(span.traceId, span.debug);
  }

  @Override public boolean isSampled(long traceId, @Nullable Boolean debug) {
    boolean result = super.isSampled(traceId, debug);
    if (result) spanCount.incrementAndGet();
    return result;
  }

  @Override protected long boundary() {
    return boundary.get();
  }
}
