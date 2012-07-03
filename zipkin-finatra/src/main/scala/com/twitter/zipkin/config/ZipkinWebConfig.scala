package com.twitter.zipkin.config

import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.web.{Resource, ZipkinWeb, App}

trait ZipkinWebConfig extends ZipkinConfig[ZipkinWeb] {

  var serverPort : Int = 8080
  var adminPort  : Int = 9902

  var rootUrl: String = ""

  /* Map dirname to content type */
  var resourceDirs: Map[String, String] = Map[String, String](
    "css" -> "text/css",
    "img" -> "image/png",
    "js" -> "application/javascript",
    "templates" -> "text/plain"
  )

  def appConfig: () => App = () => new App
  lazy val app = appConfig()

  def resourceConfig: () => Resource = () => new Resource(resourceDirs)
  lazy val resource = resourceConfig()

  def apply(runtime: RuntimeEnvironment) = {
    new ZipkinWeb(this)
  }
}
