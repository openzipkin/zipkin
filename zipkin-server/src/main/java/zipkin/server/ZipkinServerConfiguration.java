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
package zipkin.server;

import com.github.kristofa.brave.Brave;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.Codec;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.InMemoryStorage;
import zipkin.SpanStore;
import zipkin.StorageComponent;
import zipkin.cassandra.SessionFactory;
import zipkin.jdbc.JDBCStorage;
import zipkin.kafka.KafkaCollector;
import zipkin.scribe.ScribeCollector;
import zipkin.server.brave.TracedSpanStore;

@Configuration
public class ZipkinServerConfiguration {

  @Bean
  @ConditionalOnMissingBean(Codec.Factory.class)
  Codec.Factory codecFactory() {
    return Codec.FACTORY;
  }

  @Bean
  @ConditionalOnMissingBean(CollectorSampler.class)
  CollectorSampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
    return CollectorSampler.create(rate);
  }

  @Bean
  @ConditionalOnMissingBean(CollectorMetrics.class)
  CollectorMetrics metrics(CounterService counterService, GaugeService gaugeService) {
    return new ActuateCollectorMetrics(counterService, gaugeService);
  }

  /**
   * SpanStore is explicitly marked as a bean, so that it can be wrapped with a {@link
   * TracedSpanStore}.
   *
   * <p>Lazy to ensure that transient storage errors don't crash bootstrap.
   */
  @Bean @Lazy SpanStore spanStore(StorageComponent storage) {
    return storage.spanStore();
  }

  @Configuration
  @ConditionalOnSelfTracing
  static class BraveSpanStoreEnhancer implements BeanPostProcessor {

    @Autowired(required = false)
    Brave brave;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (bean instanceof SpanStore && brave != null) {
        return new TracedSpanStore(brave, (SpanStore) bean);
      }
      return bean;
    }
  }

  @Configuration
  // "matchIfMissing = true" ensures this is used when there's no configured storage type
  @ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mem", matchIfMissing = true)
  @ConditionalOnMissingBean(StorageComponent.class)
  static class InMemoryConfiguration {
    @Bean StorageComponent storage() {
      return new InMemoryStorage();
    }
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinMySQLProperties.class)
  @ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mysql")
  @ConditionalOnMissingBean(StorageComponent.class)
  static class JDBCConfiguration {
    @Autowired(required = false)
    DataSource dataSource;

    @Autowired(required = false)
    ZipkinMySQLProperties mysql;

    @Autowired(required = false)
    @Qualifier("tracingExecuteListenerProvider")
    ExecuteListenerProvider listener;

    @Bean @ConditionalOnMissingBean(Executor.class)
    public Executor executor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setThreadNamePrefix("JDBCStorage-");
      executor.initialize();
      return executor;
    }

    @Bean StorageComponent storage(Executor executor) {
      return JDBCStorage.builder()
          .executor(executor)
          .datasource(dataSource != null ? dataSource : initializeFromMySQLProperties())
          .listenerProvider(listener).build();
    }

    DataSource initializeFromMySQLProperties() {
      StringBuilder url = new StringBuilder("jdbc:mysql://");
      url.append(mysql.getHost()).append(":").append(mysql.getPort());
      url.append("/").append(mysql.getDb());
      url.append("?autoReconnect=true");
      url.append("&useSSL=").append(mysql.isUseSsl());
      url.append("&maxActive=").append(mysql.getMaxActive());
      return DataSourceBuilder.create()
          .driverClassName("org.mariadb.jdbc.Driver")
          .url(url.toString())
          .username(mysql.getUsername())
          .password(mysql.getPassword())
          .build();
    }
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinCassandraProperties.class)
  @ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "cassandra")
  @ConditionalOnMissingBean(StorageComponent.class)
  static class CassandraConfiguration {

    @Autowired(required = false)
    @Qualifier("tracingSessionFactory")
    SessionFactory tracingSessionFactory;

    @Bean StorageComponent storage(ZipkinCassandraProperties properties) {
      return tracingSessionFactory == null
          ? properties.toBuilder().build()
          : properties.toBuilder().sessionFactory(tracingSessionFactory).build();
    }
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinElasticsearchProperties.class)
  @ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
  @ConditionalOnMissingBean(StorageComponent.class)
  static class ElasticsearchConfiguration {
    @Bean StorageComponent storage(ZipkinElasticsearchProperties elasticsearch) {
      return elasticsearch.toBuilder().build();
    }
  }

  /**
   * This collector accepts Scribe logs in a specified category. Each log entry is expected to
   * contain a single span, which is TBinaryProtocol big-endian, then base64 encoded. Decoded spans
   * are stored asynchronously.
   */
  @Configuration
  @EnableConfigurationProperties(ZipkinScribeProperties.class)
  @ConditionalOnClass(name = "zipkin.scribe.ScribeCollector")
  static class ScribeConfiguration {
    @Bean ScribeCollector scribe(ZipkinScribeProperties scribe, CollectorSampler sampler,
        CollectorMetrics metrics, StorageComponent storage) {
      return scribe.toBuilder().sampler(sampler).metrics(metrics).build(storage);
    }
  }

  /**
   * This collector consumes a topic, decodes spans from thrift messages and stores them subject to
   * sampling policy.
   */
  @Configuration
  @EnableConfigurationProperties(ZipkinKafkaProperties.class)
  @ConditionalOnKafkaZookeeper
  static class KafkaConfiguration {
    @Bean KafkaCollector kafka(ZipkinKafkaProperties kafka, CollectorSampler sampler,
        CollectorMetrics metrics, StorageComponent storage) {
      return kafka.toBuilder().sampler(sampler).metrics(metrics).build(storage);
    }
  }

  /**
   * This condition passes when Kafka classes are available and {@link
   * ZipkinKafkaProperties#getZookeeper()} is set.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Conditional(ConditionalOnKafkaZookeeper.KafkaEnabledCondition.class)
  @ConditionalOnClass(name = "zipkin.kafka.KafkaCollector") @interface ConditionalOnKafkaZookeeper {
    class KafkaEnabledCondition extends SpringBootCondition {
      @Override
      public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata a) {
        String kafkaZookeeper = context.getEnvironment().getProperty("kafka.zookeeper");
        return kafkaZookeeper == null || kafkaZookeeper.isEmpty() ?
            ConditionOutcome.noMatch("kafka.zookeeper isn't set") :
            ConditionOutcome.match();
      }
    }
  }
}
