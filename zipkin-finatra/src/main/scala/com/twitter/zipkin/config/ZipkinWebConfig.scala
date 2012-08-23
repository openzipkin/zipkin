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
package com.twitter.zipkin.config

import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time._
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.Duration
import com.twitter.zipkin.gen
import com.twitter.zipkin.web.{Resource, ZipkinWeb, App}
import com.twitter.zipkin.config.zookeeper.{ZooKeeperClientConfig, ZooKeeperConfig}
import java.net.InetSocketAddress

trait ZipkinWebConfig extends ZipkinConfig[ZipkinWeb] {

  var serverPort : Int = 8080
  var adminPort  : Int = 9902

  var rootUrl: String = "http://localhost/"
  var pinTtl: Duration = 30.days
  var hostConnectionLimit: Int = 1

  var queryServerSetPath = "/twitter/service/zipkin/query"
  def queryClient: Either[InetSocketAddress, ZooKeeperClient] = Right(zkClient)

  /* Map dirname to content type */
  var resourceDirs: Map[String, String] = Map[String, String](
    "css" -> "text/css",
    "img" -> "image/png",
    "js" -> "application/javascript",
    "templates" -> "text/plain"
  )

  var jsConfig = new JsConfig
  var cssConfig = new CssConfig

  def zkConfig: ZooKeeperConfig

  def zkClientConfig = new ZooKeeperClientConfig {
    var config = zkConfig
  }
  lazy val zkClient: ZooKeeperClient = zkClientConfig.apply()

  def appConfig: (gen.ZipkinQuery.FinagledClient) => App =
    (client) => new App(this, client)

  def resourceConfig: () => Resource = () => new Resource(resourceDirs)
  lazy val resource = resourceConfig()

  def apply(runtime: RuntimeEnvironment) = {
    new ZipkinWeb(this)
  }
}
