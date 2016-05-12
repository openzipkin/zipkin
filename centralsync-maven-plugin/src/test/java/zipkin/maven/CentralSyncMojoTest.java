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

import okhttp3.Credentials;
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
  Server bintrayServer;
  Server sonatypeServer;

  @Before
  public void setupCentralSyncMojo() {
    mojo = new CentralSyncMojo();
    mojo.version = "1.1";
    mojo.settings = new Settings();
    mojo.settings.addServer(new Server());
    mojo.baseUrl = server.url("").toString();
    bintrayServer = new Server();
    bintrayServer.setId("bintray");
    bintrayServer.setUsername("bintray-user");
    bintrayServer.setPassword("bintray-api-key");
    sonatypeServer = new Server();
    sonatypeServer.setId("sonatype");
    sonatypeServer.setUsername("sync-user");
    sonatypeServer.setPassword("sync-password");
  }

  @Test
  public void failsWhenSonatypeNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/id sonatype not found");

    mojo.settings.addServer(bintrayServer);

    mojo.execute();
  }

  @Test
  public void failsWhenSonatypeUsernameNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/sonatype/username not found");

    mojo.settings.addServer(bintrayServer);

    Server serverSettings = new Server();
    serverSettings.setId("sonatype");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void failsWhenSonatypePasswordNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/sonatype/password not found!");

    mojo.settings.addServer(bintrayServer);

    Server serverSettings = new Server();
    serverSettings.setId("sonatype");
    serverSettings.setUsername("sync-user");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void failsWhenBintrayNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/id bintray not found");

    mojo.settings.addServer(sonatypeServer);

    mojo.execute();
  }

  @Test
  public void failsWhenBintrayUsernameNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/bintray/username not found");

    mojo.settings.addServer(sonatypeServer);

    Server serverSettings = new Server();
    serverSettings.setId("bintray");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void failsWhenBintrayPasswordNotInSettings() throws MojoExecutionException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("settings/server/bintray/password not found!");

    mojo.settings.addServer(sonatypeServer);

    Server serverSettings = new Server();
    serverSettings.setId("bintray");
    serverSettings.setUsername("sync-user");
    mojo.settings.addServer(serverSettings);

    mojo.execute();
  }

  @Test
  public void doesntExecuteOnDryRun() throws MojoExecutionException {
    mojo.settings.addServer(sonatypeServer);
    mojo.settings.addServer(bintrayServer);
    mojo.dryRun = true;
    mojo.execute();

    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  public void execute() throws MojoExecutionException, InterruptedException {
    server.enqueue(new MockResponse());

    mojo.settings.addServer(sonatypeServer);
    mojo.settings.addServer(bintrayServer);
    mojo.execute();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/maven_central_sync/openzipkin/maven/zipkin-java/versions/1.1");
    assertThat(request.getBody().readUtf8()).isEqualTo("{\n"
        + "  \"username\": \"sync-user\",\n"
        + "  \"password\": \"sync-password\"\n"
        + "}");
    assertThat(request.getHeader("Authorization"))
        .isEqualTo(Credentials.basic("bintray-user", "bintray-api-key"));
    assertThat(request.getHeader("User-Agent"))
        .isEqualTo("openzipkin/zipkin-java release process, centralsync-maven-plugin");
  }
}
