/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.spring.ArmeriaAutoConfiguration;
import org.springframework.context.annotation.Import;
import zipkin2.server.internal.activemq.ZipkinActiveMQCollectorConfiguration;
import zipkin2.server.internal.brave.ZipkinSelfTracingConfiguration;
import zipkin2.server.internal.cassandra3.ZipkinCassandra3StorageConfiguration;
import zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageConfiguration;
import zipkin2.server.internal.eureka.ZipkinEurekaDiscoveryConfiguration;
import zipkin2.server.internal.health.ZipkinHealthController;
import zipkin2.server.internal.kafka.ZipkinKafkaCollectorConfiguration;
import zipkin2.server.internal.mysql.ZipkinMySQLStorageConfiguration;
import zipkin2.server.internal.prometheus.ZipkinMetricsController;
import zipkin2.server.internal.prometheus.ZipkinPrometheusMetricsConfiguration;
import zipkin2.server.internal.rabbitmq.ZipkinRabbitMQCollectorConfiguration;
import zipkin2.server.internal.scribe.ZipkinScribeCollectorConfiguration;
import zipkin2.server.internal.ui.ZipkinUiConfiguration;

@Import({
  ArmeriaAutoConfiguration.class,
  ZipkinConfiguration.class,
  ZipkinHttpConfiguration.class,
  ZipkinUiConfiguration.class,
  ZipkinCassandra3StorageConfiguration.class,
  ZipkinElasticsearchStorageConfiguration.class,
  ZipkinMySQLStorageConfiguration.class,
  ZipkinScribeCollectorConfiguration.class,
  ZipkinSelfTracingConfiguration.class,
  ZipkinQueryApiV2.class,
  ZipkinHttpCollector.class,
  ZipkinGrpcCollector.class,
  ZipkinActiveMQCollectorConfiguration.class,
  ZipkinKafkaCollectorConfiguration.class,
  ZipkinRabbitMQCollectorConfiguration.class,
  ZipkinMetricsController.class,
  ZipkinHealthController.class,
  ZipkinPrometheusMetricsConfiguration.class,
  ZipkinEurekaDiscoveryConfiguration.class,
})
public class InternalZipkinConfiguration {
}
