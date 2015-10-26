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

import ch.qos.logback.classic.{Logger, Level}
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.stats.DefaultStatsReceiver
import com.twitter.finagle.tracing.{DefaultTracer, NullTracer}
import com.twitter.finagle.zipkin.thrift.{SpanStoreZipkinTracer, RawZipkinTracer}
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.query.ZipkinQueryServer
import com.twitter.zipkin.storage.{DependencyStore, NullDependencyStore, SpanStore}
import org.slf4j.LoggerFactory

case class QueryServiceBuilder(override val defaultFinatraHttpPort: String = "0.0.0.0:9411",
                               override val defaultHttpPort: Int = 9901,
                               logLevel: String = "INFO",
                               spanStore: SpanStore,
                               dependencies: DependencyStore = new NullDependencyStore
                                ) extends ZipkinQueryServer(spanStore, dependencies) with
                                          Builder[RuntimeEnvironment => ListeningServer] {

  override def apply() = (runtime: RuntimeEnvironment) => {
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[Logger].setLevel(Level.toLevel(logLevel))

    /** If the span transport is set, trace accordingly, or disable tracing */
    DefaultTracer.self = sys.env.get("TRANSPORT_TYPE") match {
      case Some("scribe") => RawZipkinTracer(sys.env.get("SCRIBE_HOST").getOrElse("localhost"), sys.env.get("SCRIBE_PORT").getOrElse("1463").toInt)
      case Some("http") => new SpanStoreZipkinTracer(spanStore, DefaultStatsReceiver.get)
      case _ => NullTracer
    }

    nonExitingMain(Array(
      "-local.doc.root", "/"
    ))
    adminHttpServer
  }
}
