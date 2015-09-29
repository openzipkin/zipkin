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
package com.twitter.zipkin.collector.builder

import com.twitter.finagle.stats.{OstrichStatsReceiver, StatsReceiver}
import com.twitter.logging.config._
import com.twitter.logging.{ConsoleHandler, Logger, LoggerFactory}
import com.twitter.ostrich.admin._
import java.net.{InetAddress, InetSocketAddress}
import com.twitter.zipkin.builder.Builder

import scala.util.matching.Regex

/**
 * Base builder for a Zipkin service
 */
case class ZipkinServerBuilder(
  serverPort              : Int,
  adminPort               : Int,
  serverAddress           : InetAddress              = InetAddress.getByAddress(Array[Byte](0,0,0,0)),
  adminStatsNodes         : List[StatsFactory]       = List(StatsFactory(reporters = List(TimeSeriesCollectorFactory()))),
  adminStatsFilters       : List[Regex]              = List.empty,
  statsReceiver           : StatsReceiver            = new OstrichStatsReceiver
) extends Builder[(RuntimeEnvironment) => Unit] {

  def serverPort(p: Int)                : ZipkinServerBuilder = copy(serverPort        = p)
  def adminPort(p: Int)                 : ZipkinServerBuilder = copy(adminPort         = p)
  def serverAddress(a: InetAddress)     : ZipkinServerBuilder = copy(serverAddress     = a)
  def statsReceiver(s: StatsReceiver)   : ZipkinServerBuilder = copy(statsReceiver     = s)

  def addAdminStatsNode(n: StatsFactory): ZipkinServerBuilder = copy(adminStatsNodes   = adminStatsNodes :+ n)
  def addAdminStatsFilter(f: Regex)     : ZipkinServerBuilder = copy(adminStatsFilters = adminStatsFilters :+ f)

  private lazy val adminServiceFactory: AdminServiceFactory =
    AdminServiceFactory(
      httpPort = adminPort,
      statsNodes = adminStatsNodes,
      statsFilters = adminStatsFilters
    )

  lazy val socketAddress = new InetSocketAddress(serverAddress, serverPort)

  var adminHttpService: Option[AdminHttpService] = None

  def apply() = (runtime: RuntimeEnvironment) => {
    adminHttpService = Some(adminServiceFactory(runtime))
  }
}
