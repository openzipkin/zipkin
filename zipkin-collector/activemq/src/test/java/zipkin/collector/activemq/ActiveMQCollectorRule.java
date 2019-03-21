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
package zipkin.collector.activemq;

import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import zipkin2.CheckResult;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

import java.io.IOException;

class ActiveMQCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQCollectorRule.class);

  final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();

  GenericContainer container;
  ActiveMQCollector collector;



  @Override
  protected void before() throws Throwable {

    try {
      this.collector = tryToInitializeCollector();
    } catch (RuntimeException| Error e) {
      if (container == null) throw e;
      container.stop();
      container = null; // try with local connection instead
      this.collector = tryToInitializeCollector();
    }
  }

  ActiveMQCollector tryToInitializeCollector() {
    ActiveMQCollector result = computeCollectorBuilder().build();
    result.start();

    CheckResult check = result.check();
    if (!check.ok()) {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
    return result;
  }

  ActiveMQCollector.Builder computeCollectorBuilder() {
    return ActiveMQCollector.builder()
      .storage(storage)
      .metrics(metrics)
      .queue("zipkin")
      .addresses("tcp://localhost:61616");
  }



  @Override
  protected void after() {
    try {
      if (collector != null) collector.close();
    } catch (IOException e) {
      LOGGER.warn("error closing collector " + e.getMessage(), e);
    } finally {
      if (container != null) {
        container.stop();
      }
    }
  }
}
