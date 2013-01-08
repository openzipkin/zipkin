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

import com.twitter.finagle.stats.{StatsReceiver, OstrichStatsReceiver}
import com.twitter.finagle.tracing.{Tracer, NullTracer}
import com.twitter.logging.{Logger, LoggerFactory}
import com.twitter.ostrich.admin._
import com.twitter.util.{Timer, JavaTimer}
import scala.util.matching.Regex

case class ZipkinServerBuilder(
  serverPort       : Int,
  adminPort        : Int,
  loggers          : List[LoggerFactory] = List.empty,
  adminStatsNodes  : List[StatsFactory]  = List(StatsFactory(reporters = List(TimeSeriesCollectorFactory()))),
  adminStatsFilters: List[Regex]         = List.empty,
  statsReceiver    : StatsReceiver       = new OstrichStatsReceiver,
  tracerFactory    : Tracer.Factory      = NullTracer.factory,
  timer            : Timer               = new JavaTimer(true)
) extends Builder[RuntimeEnvironment => Unit] {

  def serverPort(p: Int)                : ZipkinServerBuilder = copy(serverPort        = p)
  def adminPort(p: Int)                 : ZipkinServerBuilder = copy(adminPort         = p)
  def addLogger(l: LoggerFactory)       : ZipkinServerBuilder = copy(loggers           = loggers :+ l)
  def addAdminStatsNode(n: StatsFactory): ZipkinServerBuilder = copy(adminStatsNodes   = adminStatsNodes :+ n)
  def addAdminStatsFilter(f: Regex)     : ZipkinServerBuilder = copy(adminStatsFilters = adminStatsFilters :+ f)
  def statsReceiver(s: StatsReceiver)   : ZipkinServerBuilder = copy(statsReceiver     = s)
  def tracerFactory(t: Tracer.Factory)  : ZipkinServerBuilder = copy(tracerFactory     = t)
  def timer(t: Timer)                   : ZipkinServerBuilder = copy(timer             = t)

  private def adminServiceFactory: AdminServiceFactory =
    AdminServiceFactory(
      httpPort = adminPort,
      statsNodes = adminStatsNodes,
      statsFilters = adminStatsFilters
    )

  def apply(): (RuntimeEnvironment) => Unit = (runtime: RuntimeEnvironment) => {
    Logger.configure(loggers)
    adminServiceFactory(runtime)
  }
}
