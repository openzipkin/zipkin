/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.testcontainers.utility.DockerImageName.parse;

class ElasticsearchExtension extends ElasticsearchBaseExtension {
  static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchExtension.class);

  ElasticsearchExtension(int majorVersion) {
    super(new ElasticsearchContainer(majorVersion));
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class ElasticsearchContainer extends GenericContainer<ElasticsearchContainer> {
    ElasticsearchContainer(int majorVersion) {
      super(parse("ghcr.io/openzipkin/zipkin-elasticsearch" + majorVersion + ":3.3.1"));
      addExposedPort(9200);
      waitStrategy = Wait.forHealthcheck();
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
