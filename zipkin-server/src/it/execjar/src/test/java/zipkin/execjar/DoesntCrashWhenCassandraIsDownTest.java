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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DoesntCrashWhenCassandraIsDownTest {

  @Rule
  public ExecJarRule zipkin = new ExecJarRule()
      .putEnvironment("STORAGE_TYPE", "cassandra")
      .putEnvironment("CASSANDRA_CONTACT_POINTS", "idontexist");

  @Test
  public void startsButReturns500QueryingStorage() {
    try {
      HttpURLConnection connection = (HttpURLConnection)
          URI.create("http://localhost:" + zipkin.port() + "/api/v1/services").toURL()
              .openConnection();

      assertEquals(500, connection.getResponseCode());
    } catch (RuntimeException | IOException e) {
      fail(String.format("unexpected error!%s%n%s", e.getMessage(), zipkin.consoleOutput()));
    }
  }

  @Test
  public void startsButReturnsFailedHealthCheck() {
    try {
      HttpURLConnection connection = (HttpURLConnection)
          URI.create("http://localhost:" + zipkin.port() + "/health").toURL()
              .openConnection();

      assertEquals(503, connection.getResponseCode());
    } catch (RuntimeException | IOException e) {
      fail(String.format("unexpected error!%s%n%s", e.getMessage(), zipkin.consoleOutput()));
    }
  }
}
