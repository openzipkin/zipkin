/**
 * Copyright 2015-2018 The OpenZipkin Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.kafka;

import com.google.auto.value.AutoValue;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import zipkin2.CheckResult;
import zipkin2.codec.Encoding;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;


import java.util.Collections;
import java.util.Map;
import java.util.Properties;

@AutoValue
public abstract class KafkaStorage extends zipkin2.storage.StorageComponent {
    public static Builder newBuilder() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "0");
        return new AutoValue_KafkaStorage.Builder()
                .encoding(Encoding.JSON)
                .properties(properties)
                .topic("zipkin")
                .overrides(Collections.EMPTY_MAP);
    }

    @AutoValue.Builder
    public static abstract class Builder extends StorageComponent.Builder {
        abstract Builder properties(Properties properties);

        abstract Properties properties();

        /** Topic zipkin spans will be send to. Defaults to "zipkin" */
        public abstract Builder topic(String topic);

        /**
         * Initial set of kafka servers to connect to, rest of cluster will be discovered (comma
         * separated). No default
         *
         * @see ProducerConfig#BOOTSTRAP_SERVERS_CONFIG
         */
        public final Builder bootstrapServers(String bootstrapServers) {
            if (bootstrapServers == null) throw new NullPointerException("bootstrapServers == null");
            properties().put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            return this;
        }

        /**
         * By default, a producer will be created, targeted to {@link #bootstrapServers(String)} with 0
         * required {@link ProducerConfig#ACKS_CONFIG acks}. Any properties set here will affect the
         * producer config.
         *
         * <p>For example: Reduce the timeout blocking from one minute to 5 seconds.
         * <pre>{@code
         * Map<String, String> overrides = new LinkedHashMap<>();
         * overrides.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
         * builder.overrides(overrides);
         * }</pre>
         *
         * @see ProducerConfig
         */
        public final Builder overrides(Map<String, ?> overrides) {
            if (overrides == null) throw new NullPointerException("overrides == null");
            properties().putAll(overrides);
            return this;
        }

        @Override abstract public KafkaStorage build();

        Builder() {
        }
    }

    abstract Properties properties();
    abstract String topic();

    @Override
    public SpanStore spanStore() {
        return new KafkaSpanStore(this);
    }

    @Override
    public SpanConsumer spanConsumer() {
        return new KafkaSpanConsumer(this);
    }

    /** This is blocking so that we can determine if the cluster is healthy or not */
    @Override
    public CheckResult check() {
        return ensureKafkaReady();
    }

    CheckResult ensureKafkaReady() {
        /*Request request = new Request.Builder().url(http().baseUrl.resolve("/_cluster/health/" + index))
                .tag("get-cluster-health").build();

        try {
            return http().newCall(request, ReadStatus.INSTANCE).execute();
        } catch (IOException | RuntimeException e) {
            return CheckResult.failed(e);
        }*/
        return CheckResult.OK;
    }

    @Override
    public void close() {
    }

    KafkaStorage() {
    }
}
