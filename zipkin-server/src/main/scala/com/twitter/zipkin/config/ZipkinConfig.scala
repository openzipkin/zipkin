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
package com.twitter.zipkin.config

import com.twitter.finagle.stats.{OstrichStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.{NullTracer, Tracer}
import com.twitter.logging.config._
import com.twitter.logging.{ConsoleHandler, LoggerFactory, Logger}
import com.twitter.util.{JavaTimer, Timer, Config}
import com.twitter.ostrich.admin._
import scala.util.matching.Regex

trait ZipkinConfig[T <: Service] extends Config[RuntimeEnvironment => T] {

  /* The port on which the server runs */
  var serverPort: Int

  /* The port on which the admin interface runs */
  var adminPort: Int

  lazy val statsReceiver: StatsReceiver = new OstrichStatsReceiver

  var loggers: List[LoggerFactory] =
    LoggerFactory(
      level = Some(Level.DEBUG),
      handlers = ConsoleHandler() :: Nil
    ) :: Nil

  lazy val log = Logger.get("ZipkinConfig")

  lazy val tracerFactory: Tracer.Factory = NullTracer.factory

  lazy val timer: Timer = new JavaTimer(true)

  var adminStatsNodes: List[StatsFactory] =
    StatsFactory(reporters = List(TimeSeriesCollectorFactory())) :: Nil

  var adminStatsFilters: List[Regex] = List.empty

  def adminServiceFactory: AdminServiceFactory =
    AdminServiceFactory(
      httpPort = adminPort,
      statsNodes = adminStatsNodes,
      statsFilters = adminStatsFilters
    )

  def initializeLoggers() {
    Logger.configure(loggers)
  }

  def initializeAdminService(runtime: RuntimeEnvironment) =
    adminServiceFactory(runtime)

  def apply(): (RuntimeEnvironment) => T = (runtime: RuntimeEnvironment) => {
    initializeLoggers()
    initializeAdminService(runtime)
    val service = apply(runtime)
    ServiceTracker.register(service)
    service
  }

  def apply(runtime: RuntimeEnvironment): T
}
