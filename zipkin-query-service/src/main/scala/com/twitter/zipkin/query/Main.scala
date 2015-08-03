/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.query

import com.google.common.base.Charsets.UTF_8
import com.google.common.io.{Resources, Files}
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{ServiceTracker, RuntimeEnvironment}
import com.twitter.util.Eval
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.BuildProperties

object Main {
  val log = Logger.get(getClass.getName)

  def main(args: Array[String]) {
    log.info("Loading configuration")
    val runtime = RuntimeEnvironment(BuildProperties, args)

    // Fallback to bundled config resources, if there's no file at the path specified as -f
    val source = if (runtime.configFile.exists()) Files.toString(runtime.configFile, UTF_8)
    else Resources.toString(getClass.getResource(runtime.configFile.toString), UTF_8)

    val builder = (new Eval).apply[Builder[RuntimeEnvironment => ZipkinQuery]](source)
    try {
      val server = builder.apply().apply(runtime)
      server.start()
      ServiceTracker.register(server)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        log.error(e, "Unexpected exception: %s", e.getMessage)
        System.exit(0)
    }
  }
}
