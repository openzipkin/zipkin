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
package com.twitter.zipkin.builder

import com.twitter.finagle.ListeningServer
import com.twitter.finagle.tracing.{DefaultTracer, NullTracer}
import com.twitter.finagle.zipkin.thrift.RawZipkinTracer
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.query.ZipkinQueryServer
import com.twitter.zipkin.storage.Store

case class QueryServiceBuilder(
  storeBuilder: Builder[Store],
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(9411, 9901)
) extends Builder[RuntimeEnvironment => ListeningServer] {

  def apply(): (RuntimeEnvironment) => ListeningServer = (runtime: RuntimeEnvironment) => {
    // If a scribe host is configured, send all traces to it, otherwise disable tracing
    val scribeHost = sys.env.get("SCRIBE_HOST")
    val scribePort = sys.env.get("SCRIBE_PORT")
    DefaultTracer.self = if (scribeHost.isDefined || scribePort.isDefined) {
      RawZipkinTracer(scribeHost.getOrElse("localhost"), scribePort.getOrElse("1463").toInt)
    } else {
      NullTracer
    }

    val store = storeBuilder.apply()
    object Main extends ZipkinQueryServer(store.spanStore, store.dependencies) {
      def admin = adminHttpServer
      override val defaultFinatraHttpPort = serverBuilder.serverAddress.getHostAddress + ":" + serverBuilder.serverPort
      override val defaultHttpPort = serverBuilder.adminPort
    }
    Main.nonExitingMain(Array(
      "-local.doc.root", "/"
    ))
    Main.admin
  }
}
