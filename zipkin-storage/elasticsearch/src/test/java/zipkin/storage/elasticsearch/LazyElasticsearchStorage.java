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
package zipkin.storage.elasticsearch;

import com.google.common.base.Throwables;
import org.junit.AssumptionViolatedException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HttpWaitStrategy;
import zipkin.Component;
import zipkin.internal.LazyCloseable;

import java.io.IOException;

public abstract class LazyElasticsearchStorage extends LazyCloseable<ElasticsearchStorage> {

  final String image;

  GenericContainer container;

  public LazyElasticsearchStorage(String image) {
    this.image = image;
  }

  protected String getEndpoint(int port) {
    String host = "localhost";

    try {
      if (container.isRunning()) {
        host = container.getContainerIpAddress();
        port = container.getMappedPort(port);
      }
    } catch (Exception e) {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
    }

    return String.format("%s:%d", host, port);
  }

  @Override protected ElasticsearchStorage compute() {
    try {
      container = new GenericContainer(image)
          .withExposedPorts(9200, 9300)
          .waitingFor(new HttpWaitStrategy().forPath("/"));
      container.start();
      System.out.println("Will use TestContainers Elasticsearch instance");
    } catch (Exception e) {
      // Do nothing
    }

    ElasticsearchStorage result = computeStorageBuilder().build();
    Component.CheckResult check = result.check();
    if (check.ok) {
      return result;
    } else {
      throw new AssumptionViolatedException(check.exception.getMessage(), check.exception);
    }
  }

  public abstract ElasticsearchStorage.Builder computeStorageBuilder();

  @Override public void close() {
    try {
      ElasticsearchStorage storage = maybeNull();

      if (storage != null) storage.close();
    } catch (IOException e) {
      Throwables.propagate(e);
    } finally {
      if (container != null) container.stop();
    }
  }
}
