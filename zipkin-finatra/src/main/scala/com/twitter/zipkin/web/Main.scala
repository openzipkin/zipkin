package com.twitter.zipkin.web

import com.twitter.logging.Logger
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.BuildProperties

object Main {
  val log = Logger.get(getClass.getName)

  def main(args: Array[String]) {
    log.info("Loading configuration")
    val runtime = RuntimeEnvironment(BuildProperties, args)
    val server = runtime.loadRuntimeConfig[ZipkinWeb]()
    try {
      server.start()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        log.error(e, "Unexpected exception: %s", e.getMessage)
        System.exit(0)
    }
  }
}
