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
package zipkin;

import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

/**
 * Trigger a sync from BinTray to Maven Central
 */
@Mojo( name = "sync", defaultPhase = LifecyclePhase.DEPLOY )
public class CentralSyncMojo
    extends AbstractMojo
{
    @Parameter
    private boolean dryRun = false;

    @Parameter
    private String repository;

    @Parameter
    private String packageName;

    @Parameter
    private String version;

    @Override
    public void execute() throws MojoExecutionException {
        String pkgPath = String.format("%s/%s", this.repository, this.packageName);
        if (this.dryRun) {
            this.getLog().info(String.format("(Dry run) Sync to Maven Central performed '%s/%s'.", pkgPath, this.version));
            return;
        }
        try {
            Response response = Request.Post(String.format("https://api.bintray.com/maven_central_sync/%s/versions/%s", pkgPath, this.version))
                    .bodyForm(Form.form().add("username",  "TBD-configurable-username").add("password",  "TBD-configurable-password").build())
                    .userAgent("openzipkin/zipkin-java release process, centralsync-maven-plugin")
                    .execute();
            this.getLog().info(response.returnContent().asString());
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            throw new MojoExecutionException("API call failed", e);
        }
    }
}
