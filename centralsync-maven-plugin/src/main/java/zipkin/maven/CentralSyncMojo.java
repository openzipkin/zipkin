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
import okhttp3.FormBody;
import okhttp3.HttpUrl;
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
  final OkHttpClient client = new OkHttpClient();

  @Parameter(defaultValue = "${project.version}", required = true)
  String version;

  @Parameter(defaultValue = "${settings}", required = true)
  Settings settings;

  // optional parameters

  @Parameter(property = "sync.dryRun")
  boolean dryRun = false;

  /** settings/server/id containing the username and password */
  @Parameter
  String serverId = "bintray";

  @Parameter
  String baseUrl = "https://api.bintray.com";

  @Parameter
  String repository = "maven";

  @Parameter
  String packageName = "zipkin-java";

  @Override
  public void execute() throws MojoExecutionException {
    Server server = settings.getServer(serverId);
    if (server == null) {
      throw new IllegalStateException("settings/server/id " + serverId + " not found!");
    }
    if (server.getUsername() == null) {
      throw new IllegalStateException("settings/server/" + serverId + "/username not found!");
    }
    if (server.getPassword() == null) {
      throw new IllegalStateException("settings/server/" + serverId + "/password not found!");
    }

    RequestBody formBody = new FormBody.Builder()
        .add("username", server.getUsername())
        .add("password", server.getPassword())
        .build();
    HttpUrl url = HttpUrl.parse(baseUrl).newBuilder()
        .addPathSegment("maven_central_sync")
        .addPathSegment(repository)
        .addPathSegment(packageName)
        .addPathSegment("versions")
        .addPathSegment(version).build();
    Request request = new Request.Builder()
        .url(url)
        .addHeader("User-Agent", "openzipkin/zipkin-java release process, centralsync-maven-plugin")
        .post(formBody)
        .build();
    if (dryRun) {
      getLog().info("(Dry run) Would Sync to Maven Central via: POST " + request.url());
      return;
    }
    try {
      Response response = client.newCall(request).execute();
      getLog().info(response.body().string());
    } catch (IOException e) {
      throw new MojoExecutionException("API call failed", e);
    }
  }
}
