package com.twitter.zipkin.config

import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.gen
import com.twitter.zipkin.web.{Resource, ZipkinWeb, App}
import zookeeper.{ZooKeeperClientConfig, ZooKeeperConfig}

trait ZipkinWebConfig extends ZipkinConfig[ZipkinWeb] {

  var serverPort : Int = 8080
  var adminPort  : Int = 9902

  var rootUrl: String = ""

  var queryServerSetPath = "/twitter/service/zipkin/query"

  /* Map dirname to content type */
  var resourceDirs: Map[String, String] = Map[String, String](
    "css" -> "text/css",
    "img" -> "image/png",
    "js" -> "application/javascript",
    "templates" -> "text/plain"
  )

  def zkConfig: ZooKeeperConfig

  def zkClientConfig = new ZooKeeperClientConfig {
    var config = zkConfig
  }
  lazy val zkClient: Option[ZooKeeperClient] = Some { zkClientConfig.apply() }

  def appConfig: (gen.ZipkinQuery.FinagledClient) => App =
    (client) => new App(client)

  def resourceConfig: () => Resource = () => new Resource(resourceDirs)
  lazy val resource = resourceConfig()

  def apply(runtime: RuntimeEnvironment) = {
    new ZipkinWeb(this)
  }
}
