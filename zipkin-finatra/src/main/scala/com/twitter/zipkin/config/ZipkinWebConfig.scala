package com.twitter.zipkin.config

import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.web.{ZipkinWeb, App}

trait ZipkinWebConfig extends ZipkinConfig[ZipkinWeb] {

  var serverPort : Int = 8080
  var adminPort  : Int = 9902

  var docroot: String = "public"

  def appConfig: () => App = () => new App
  lazy val app = appConfig()

  def apply(runtime: RuntimeEnvironment) = {
    new ZipkinWeb(this)
  }
}
