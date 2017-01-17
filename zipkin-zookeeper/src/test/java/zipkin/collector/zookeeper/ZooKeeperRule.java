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

import java.util.concurrent.TimeUnit;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.Timing;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import zipkin.internal.Util;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.curator.framework.CuratorFrameworkFactory.newClient;
import static zipkin.internal.Util.propagateIfFatal;

final class ZooKeeperRule implements TestRule {
  TestingCluster cluster;
  CuratorFramework client;
  Timing timing = new Timing();

  void sleepABit() throws InterruptedException {
    timing.sleepABit();
  }

  void create(String path, String data) throws Exception {
    client.createContainers(path);
    client.setData().forPath(path, data.getBytes(Util.UTF_8));
    sleepABit();
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        for (int i = 1; i <= 3; i++) {
          try {
            doEvaluate(base);
            break;
          } catch (Throwable t) {
            propagateIfFatal(t);
            if (i == 3) {
              throw t;
            }
            Thread.sleep(1000);
          }
        }
      }
    };
  }

  void doEvaluate(Statement base) throws Throwable {
    try {
      cluster = new TestingCluster(3);
      cluster.start();

      client = newClient(cluster.getConnectString(), new RetryOneTime(200 /* ms */));
      client.start();

      checkState(client.blockUntilConnected(5, TimeUnit.SECONDS),
          "failed to connect to zookeeper in 5 seconds");

      base.evaluate();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while connecting to ZooKeeper", e);
    } finally {
      client.close();
      cluster.close();
    }
  }
}
