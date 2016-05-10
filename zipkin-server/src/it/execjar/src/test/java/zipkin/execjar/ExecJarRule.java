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
package zipkin.execjar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import zipkin.server.ZipkinServer;

public final class ExecJarRule implements TestRule {

  public ExecJarRule putEnvironment(String key, String value) {
    environment.put(key, value);
    return this;
  }

  public String consoleOutput() {
    return String.join("\n", console);
  }

  public synchronized int httpPort() throws IOException {
    if (port != null) return port;
    try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0)) {
      return (this.port = socket.getLocalPort());
    }
  }

  private Map<String, String> environment = new LinkedHashMap<>();
  private Integer port;
  private Process zipkin;
  private ConcurrentLinkedQueue<String> console = new ConcurrentLinkedQueue<>();

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        try {
          Class<?> startClass = ZipkinServer.class;
          String jar = startClass.getProtectionDomain().getCodeSource().getLocation().getFile();

          ProcessBuilder zipkinBuilder = new ProcessBuilder("java", "-jar", jar);
          zipkinBuilder.environment().put("QUERY_PORT", String.valueOf(httpPort()));
          zipkinBuilder.environment().putAll(environment);
          zipkinBuilder.redirectErrorStream(true);
          zipkin = zipkinBuilder.start();

          CountDownLatch startedOrCrashed = new CountDownLatch(1);
          Thread consoleReader = new Thread(() -> {
            boolean foundStartMessage = false;
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(zipkin.getInputStream()))) {
              String line;
              while ((line = reader.readLine()) != null) {
                if (line.indexOf("Started " + startClass.getSimpleName()) != -1) {
                  foundStartMessage = true;
                  startedOrCrashed.countDown();
                }
                console.add(line);
              }
            } catch (Exception e) {
            } finally {
              if (!foundStartMessage) startedOrCrashed.countDown();
            }
          });
          consoleReader.setDaemon(true);
          consoleReader.start();

          if (!startedOrCrashed.await(10, TimeUnit.SECONDS)) {
            throw new AssumptionViolatedException("Took too long to start or crash");
          }

          base.evaluate();
        } finally {
          zipkin.destroy();
          environment.clear();
        }
      }
    };
  }
}
