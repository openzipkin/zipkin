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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

/**
 * Triggers a sync from BinTray to Maven Central
 */
@Mojo(name = "sync", defaultPhase = LifecyclePhase.DEPLOY)
public class CentralSyncMojo extends AbstractMojo {
  final OkHttpClient client = new OkHttpClient.Builder()
      .readTimeout(10, TimeUnit.MINUTES) // central sync is synchronous and takes a long time
      .build();

  @Parameter(defaultValue = "${project.version}", required = true)
  String version;

  @Parameter(defaultValue = "${settings}", required = true)
  Settings settings;

  // optional parameters

  @Parameter(property = "sync.dryRun")
  boolean dryRun = false;

  /** settings/server/id containing the username and password */
  @Parameter
  String bintrayServerId = "bintray";

  @Parameter
  String sonatypeServerId = "sonatype";

  @Parameter
  String baseUrl = "https://api.bintray.com";

  @Parameter
  String subject = "openzipkin";

  @Parameter
  String repo = "maven";

  @Parameter
  String packageName = "zipkin-java";

  @Override
  public void execute() throws MojoExecutionException {
    Server bintray = verifyCredentialsPresent(bintrayServerId);
    Server sonatype = verifyCredentialsPresent(sonatypeServerId);

    HttpUrl url = HttpUrl.parse(baseUrl).newBuilder()
        .addPathSegment("maven_central_sync")
        .addPathSegment(subject)
        .addPathSegment(repo)
        .addPathSegment(packageName)
        .addPathSegment("versions")
        .addPathSegment(version).build();
    Request request = new Request.Builder()
        .url(url)
        .addHeader("Authorization", Credentials.basic(bintray.getUsername(), bintray.getPassword()))
        .addHeader("User-Agent", "openzipkin/zipkin-java release process, centralsync-maven-plugin")
        .post(RequestBody.create(MediaType.parse("application/json"), "{\n"
            + "  \"username\": \"" + sonatype.getUsername() + "\",\n"
            + "  \"password\": \"" + sonatype.getPassword() + "\"\n"
            + "}"))
        .build();
    if (dryRun) {
      getLog().info("(Dry run) Would Sync to Maven Central via: POST " + request.url());
      return;
    }
    try {
      Response response = client.newCall(request).execute();
      if (response.isSuccessful()) {
        getLog().info(response.body().string());
      } else {
        throw new MojoExecutionException(request.url() + " failed: " + response);
      }
    } catch (IOException e) {
      throw new MojoExecutionException(request.url() + " failed: " + e.getMessage(), e);
    }
  }

  Server verifyCredentialsPresent(String id) {
    Server server = settings.getServer(id);
    if (server == null) {
      throw new IllegalStateException("settings/server/id " + id + " not found!");
    }
    if (server.getUsername() == null) {
      throw new IllegalStateException("settings/server/" + id + "/username not found!");
    }
    if (server.getPassword() == null) {
      throw new IllegalStateException("settings/server/" + id + "/password not found!");
    }
    return server;
  }
}
