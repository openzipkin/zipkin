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
package zipkin.collector.kafka10;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.collector.Collector;
import zipkin.collector.CollectorComponent;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.LazyCloseable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.StorageComponent;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link AsyncSpanConsumer#accept span consumer}.
 *
 * <p>This collector uses a Kafka 0.10+ consumer.
 */
public final class KafkaCollector implements CollectorComponent {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaCollector.class);

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder implements CollectorComponent.Builder {
    final Properties properties = new Properties();
    Collector.Builder delegate = Collector.builder(KafkaCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String topic = "zipkin";
    int streams = 1;

    @Override public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("kafka");
      delegate.metrics(this.metrics);
      return this;
    }

    /**
     * Topic zipkin spans will be consumed from. Defaults to "zipkin". Multiple topics may be
     * specified if comma delimited.
     */
    public Builder topic(String topic) {
      this.topic = checkNotNull(topic, "topic");
      return this;
    }

    /** The bootstrapServers connect string, ex. 127.0.0.1:9092. No default. */
    public Builder bootstrapServers(String bootstrapServers) {
      properties.put(BOOTSTRAP_SERVERS_CONFIG, checkNotNull(bootstrapServers, "bootstrapServers"));
      return this;
    }

    /** The consumer group this process is consuming on behalf of. Defaults to "zipkin" */
    public Builder groupId(String groupId) {
      properties.put(GROUP_ID_CONFIG, checkNotNull(groupId, "groupId"));
      return this;
    }

    /** Count of threads consuming the topic. Defaults to 1 */
    public Builder streams(int streams) {
      this.streams = streams;
      return this;
    }

    /**
     * By default, a consumer will be built from properties derived from builder defaults,
     * as well as "auto.offset.reset" -> "earliest". Any properties set here will override the
     * consumer config.
     *
     * <p>For example: Only consume spans since you connected by setting the below.
     * <pre>{@code
     * Map<String, String> overrides = new LinkedHashMap<>();
     * overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
     * builder.overrides(overrides);
     * }</pre>
     *
     * @see org.apache.kafka.clients.consumer.ConsumerConfig
     */
    public final Builder overrides(Map<String, ?> overrides) {
      properties.putAll(checkNotNull(overrides, "overrides"));
      return this;
    }

    @Override public KafkaCollector build() {
      return new KafkaCollector(this);
    }

    Builder() {
      // Settings below correspond to "New Consumer Configs"
      // https://kafka.apache.org/documentation/#newconsumerconfigs
      properties.put(GROUP_ID_CONFIG, "zipkin");
      properties.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
      properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
          ByteArrayDeserializer.class.getName());
      properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
          ByteArrayDeserializer.class.getName());
    }
  }

  final LazyKafkaWorkers kafkaWorkers;

  KafkaCollector(Builder builder) {
    kafkaWorkers = new LazyKafkaWorkers(builder);
  }

  @Override public KafkaCollector start() {
    kafkaWorkers.get();
    return this;
  }

  @Override public CheckResult check() {
    try {
      CheckResult failure = kafkaWorkers.failure.get(); // check the kafka workers didn't quit
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Override
  public void close() throws IOException {
    kafkaWorkers.close();
  }

  static final class LazyKafkaWorkers extends LazyCloseable<ExecutorService> {
    final int streams;
    final Builder builder;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();
    final CopyOnWriteArrayList<KafkaCollectorWorker> workers = new CopyOnWriteArrayList<>();

    LazyKafkaWorkers(Builder builder) {
      this.streams = builder.streams;
      this.builder = builder;
    }

    @Override protected ExecutorService compute() {
      ExecutorService pool = streams == 1
          ? Executors.newSingleThreadExecutor()
          : Executors.newFixedThreadPool(streams);

      for (int i = 0; i < streams; i ++) {
        final KafkaCollectorWorker worker = new KafkaCollectorWorker(builder);
        workers.add(worker);
        pool.execute(guardFailures(worker));
      }

      return pool;
    }

    Runnable guardFailures(final Runnable delegate) {
      return new Runnable() {
        @Override public void run() {
          try {
            delegate.run();
          } catch (InterruptException e) {
            LOG.info("Kafka collector worker was interrupted. This is expected during shutdown.",
              e);
            failure.set(CheckResult.failed(e));
          } catch (RuntimeException e) {
            LOG.error("Kafka collector worker exited with exception.", e);
            failure.set(CheckResult.failed(e));
          }
        }
      };
    }

    @Override
    public void close() {
      ExecutorService maybeNull = maybeNull();
      if (maybeNull != null) {
        maybeNull.shutdownNow();
        try {
          maybeNull.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          // at least we tried
        }
      }
    }
  }
}
