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

class OpenSearchExtension extends ElasticsearchBaseExtension {
  static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchExtension.class);

  OpenSearchExtension(int majorVersion) {
    super(new OpenSearchContainer(majorVersion));
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class OpenSearchContainer extends GenericContainer<OpenSearchContainer> {
      OpenSearchContainer(int majorVersion) {
      super(parse("ghcr.io/openzipkin/zipkin-opensearch" + majorVersion + ":3.3.1"));
      addExposedPort(9200);
      waitStrategy = Wait.forHealthcheck();
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
