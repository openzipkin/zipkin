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
import org.springframework.boot.loader.JarLauncher;

/**
 * This is a JUnit Rule that allows you to test your Spring Boot exec jar.
 *
 * <p>It will start on a random port, and waits until the server is started before your tests
 * execute.
 *
 * <p>Often, the test classpath interferes with your ability to test your autoconfiguration, or
 * environment mappings. This class forks a process and watches it. On failure, you can look at its
 * {@link #consoleOutput() console output} for details.
 */
public final class ExecJarRule implements TestRule {

  public ExecJarRule() {
    this.execJar = JarLauncher.class.getProtectionDomain().getCodeSource().getLocation().getFile();
  }

  /** Adds a variable to the environment used by the forked boot app. */
  public ExecJarRule putEnvironment(String key, String value) {
    environment.put(key, value);
    return this;
  }

  /** Returns stderr and stdout dumped into the same place */
  public String consoleOutput() {
    return String.join("\n", console);
  }

  /** Lazy-chooses a server port, or returns the port the server started with */
  public synchronized int port() throws IOException {
    if (port != null) return port;
    try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0)) {
      return (this.port = socket.getLocalPort());
    }
  }

  private final String execJar;
  private Map<String, String> environment = new LinkedHashMap<>();
  private Integer port;
  private Process bootApp;
  private ConcurrentLinkedQueue<String> console = new ConcurrentLinkedQueue<>();

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        try {
          ProcessBuilder bootBuilder = new ProcessBuilder("java", "-jar", execJar);
          bootBuilder.environment().put("SERVER_PORT", String.valueOf(port()));
          bootBuilder.environment().putAll(environment);
          bootBuilder.redirectErrorStream(true);
          bootApp = bootBuilder.start();

          CountDownLatch startedOrCrashed = new CountDownLatch(1);
          Thread consoleReader = new Thread(() -> {
            boolean foundStartMessage = false;
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(bootApp.getInputStream()))) {
              String line;
              while ((line = reader.readLine()) != null) {
                if (line.indexOf("JVM running for") != -1) {
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
          bootApp.destroy();
        }
      }
    };
  }
}
