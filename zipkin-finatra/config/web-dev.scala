/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import com.twitter.zipkin.config.{CssConfig, JsConfig, ZipkinWebConfig}
import com.twitter.zipkin.config.zookeeper.ZooKeeperConfig
import java.net.InetSocketAddress

new ZipkinWebConfig {
  rootUrl = "http://localhost:" + serverPort + "/"

  /**
   * Making changes to js/css can be painful with a packaged jar since a compilation is needed to
   * repackage any new changes. `resourcePathPrefix` can be hacked to point to the directory
   * on your local file system so the browser resolves it outside of the jar. Example:
   *
   * `val resourcePathPrefix = "file:///Users/username/path/to/zipkin-finatra/src/main/resources/public"`
   */
  val resourcePathPrefix = "/public"
  jsConfig = new JsConfig {
    override val pathPrefix = resourcePathPrefix
  }
  cssConfig = new CssConfig {
    override val pathPrefix = resourcePathPrefix
  }

  def zkConfig = new ZooKeeperConfig {
    servers = List("localhost:3003")
  }

  override def queryClient = Left(new InetSocketAddress("localhost", 3002))
}
