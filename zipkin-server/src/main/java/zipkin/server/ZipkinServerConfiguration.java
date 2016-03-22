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
import javax.sql.DataSource;
import org.jooq.ExecuteListenerProvider;
import org.jooq.conf.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.EnableAsync;
import zipkin.Codec;
import zipkin.InMemorySpanStore;
import zipkin.Sampler;
import zipkin.SpanStore;
import zipkin.cassandra.CassandraConfig;
import zipkin.cassandra.CassandraSpanStore;
import zipkin.elasticsearch.ElasticsearchConfig;
import zipkin.elasticsearch.ElasticsearchSpanStore;
import zipkin.jdbc.JDBCSpanStore;
import zipkin.kafka.KafkaConfig;
import zipkin.kafka.KafkaTransport;
import zipkin.server.brave.TraceWritesSpanStore;
import zipkin.spanstore.guava.BlockingGuavaSpanStore;

@Configuration
@EnableConfigurationProperties(ZipkinServerProperties.class)
@EnableAsync(proxyTargetClass = true)
public class ZipkinServerConfiguration {

  @Autowired
  ZipkinServerProperties server;

  @Bean
  @ConditionalOnMissingBean(Codec.Factory.class)
  Codec.Factory codecFactory() {
    return Codec.FACTORY;
  }

  @Bean
  @ConditionalOnMissingBean(Sampler.class)
  Sampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
    return Sampler.create(rate);
  }

  @Bean
  @ConditionalOnMissingBean(SpanStore.class)
  SpanStore spanStore() {
    if (server.getStore().getType() != ZipkinServerProperties.Store.Type.mem) {
      throw new IllegalStateException("Attempted to set storage type to "
          + server.getStore().getType() + " but could not initialize the spanstore for "
          + "that storage type. Did you include it on the classpath?");
    }
    return new InMemorySpanStore();
  }

  @Configuration
  @ConditionalOnClass(name = "com.github.kristofa.brave.Brave")
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
        return new TraceWritesSpanStore(brave, (SpanStore) bean);
      }
      return bean;
    }
  }

  @Configuration
  @ConditionalOnProperty(name = "zipkin.store.type", havingValue = "mysql")
  @ConditionalOnClass(name = "zipkin.jdbc.JDBCSpanStore")
  static class JDBCConfiguration {

    @Autowired(required = false)
    DataSource datasource;

    @Autowired(required = false)
    @Qualifier("jdbcTraceListenerProvider")
    ExecuteListenerProvider listener;

    @Bean SpanStore jdbcSpanStore() {
      return new JDBCSpanStore(datasource, new Settings().withRenderSchema(false), listener);
    }
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinCassandraProperties.class)
  @ConditionalOnProperty(name = "zipkin.store.type", havingValue = "cassandra")
  @ConditionalOnClass(name = "zipkin.cassandra.CassandraSpanStore")
  static class CassandraConfiguration {
    @Bean SpanStore cassandraSpanStore(ZipkinCassandraProperties cassandra) {
      CassandraConfig config = new CassandraConfig.Builder()
          .keyspace(cassandra.getKeyspace())
          .contactPoints(cassandra.getContactPoints())
          .localDc(cassandra.getLocalDc())
          .maxConnections(cassandra.getMaxConnections())
          .ensureSchema(cassandra.isEnsureSchema())
          .username(cassandra.getUsername())
          .password(cassandra.getPassword())
          .spanTtl(cassandra.getSpanTtl())
          .indexTtl(cassandra.getIndexTtl()).build();
      return new CassandraSpanStore(config);
    }
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinElasticsearchProperties.class)
  @ConditionalOnProperty(name = "zipkin.store.type", havingValue = "elasticsearch")
  @ConditionalOnClass(name = "zipkin.elasticsearch.ElasticsearchSpanStore")
  static class ElasticsearchConfiguration {
    @Bean SpanStore elasticsearchSpanStore(ZipkinElasticsearchProperties elasticsearch) {
      ElasticsearchConfig config = new ElasticsearchConfig.Builder()
          .cluster(elasticsearch.getCluster())
          .hosts(elasticsearch.getHosts())
          .index(elasticsearch.getIndex())
          .build();
      return new BlockingGuavaSpanStore(new ElasticsearchSpanStore(config));
    }
  }

  /**
   * This transport consumes a topic, decodes spans from thrift messages and stores them subject to
   * sampling policy.
   */
  @Configuration
  @EnableConfigurationProperties(ZipkinKafkaProperties.class)
  @ConditionalOnKafkaZookeeper
  static class KafkaConfiguration {
    @Bean KafkaTransport kafkaTransport(ZipkinKafkaProperties kafka, ZipkinSpanWriter writer) {
      KafkaConfig config = KafkaConfig.builder()
          .topic(kafka.getTopic())
          .zookeeper(kafka.getZookeeper())
          .groupId(kafka.getGroupId())
          .streams(kafka.getStreams()).build();
      return new KafkaTransport(config, writer);
    }
  }

  /**
   * This condition passes when Kafka classes are available and {@link
   * ZipkinKafkaProperties#getZookeeper()} is set.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Conditional(ConditionalOnKafkaZookeeper.KafkaEnabledCondition.class)
  @ConditionalOnClass(name = "zipkin.kafka.KafkaTransport") @interface ConditionalOnKafkaZookeeper {
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
