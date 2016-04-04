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
package zipkin.maven;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class CentralSyncMojoTest {
  @Rule
  public MockWebServer server = new MockWebServer();
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  CentralSyncMojo mojo;
  Server serverSettings;

  @Before
  public void setupCentralSyncMojo() {
    mojo = new CentralSyncMojo();
    mojo.version = "1.1";
    mojo.settings = new Settings();
    mojo.settings.addServer(new Server());
    mojo.baseUrl = server.url("").toString();
    serverSettings = new Server();
    serverSettings.setId("bintray");
    serverSettings.setUsername("sync-user");
    serverSettings.setPassword("sync-password");
  }

  @Test
  public void failsWhenServerNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/id bintray not found");

    mojo.execute();
  }

  @Test
  public void failsWhenServerUsernameNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/bintray/username not found");

    Server serverSettings = new Server();
    serverSettings.setId("bintray");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void failsWhenServerPasswordNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/bintray/password not found!");

    Server serverSettings = new Server();
    serverSettings.setId("bintray");
    serverSettings.setUsername("sync-user");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void doesntExecuteOnDryRun() throws MojoExecutionException {
    mojo.settings.addServer(serverSettings);
    mojo.dryRun = true;
    mojo.execute();

    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  public void execute() throws MojoExecutionException, InterruptedException {
    server.enqueue(new MockResponse());

    mojo.settings.addServer(serverSettings);
    mojo.execute();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/maven_central_sync/maven/zipkin-java/versions/1.1");
    assertThat(request.getBody().readUtf8()).isEqualTo("username=sync-user&password=sync-password");
    assertThat(request.getHeader("User-Agent"))
        .isEqualTo("openzipkin/zipkin-java release process, centralsync-maven-plugin");
  }
}
