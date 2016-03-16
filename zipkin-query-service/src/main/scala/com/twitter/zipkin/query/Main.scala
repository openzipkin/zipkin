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

import ch.qos.logback.classic.{Level, Logger}
import com.google.common.base.Charsets.UTF_8
import com.google.common.io.{Files, Resources}
import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.util.Eval
import com.twitter.zipkin.builder.QueryServiceBuilder
import org.slf4j.LoggerFactory
import java.io.{File, FileNotFoundException}

object Main extends App {
  val scalaConfig = flag[String]("f", "path to scala configuration file. Searches for a file path first, then inside the jar.")

  def main() {
    // Fallback to bundled config resources, if there's no file at the path specified as -f
    val source = if (new File(scalaConfig()).exists()) {
      Files.toString(new File(scalaConfig()), UTF_8)
    } else {
      val resource = getClass.getResource(scalaConfig())
      if (resource == null) {
        throw new FileNotFoundException("scala configuration file not found: " + scalaConfig())
      }
      Resources.toString(resource, UTF_8)
    }

    val query = (new Eval).apply[QueryServiceBuilder](source)
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[Logger].setLevel(Level.toLevel(query.logLevel))

    // Note: this is blocking, so nothing after this will be called.
    val defaultLookback = sys.env.get("QUERY_LOOKBACK").getOrElse(7.days.inMillis.toString)
    query.nonExitingMain(Array(
      "-doc.root", "zipkin-ui",
      "-zipkin.queryService.lookback", defaultLookback
    ))
  }
}
