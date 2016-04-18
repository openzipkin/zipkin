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
package com.twitter.zipkin.sampler

import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import com.google.common.collect.ImmutableMap
import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.Filter
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.io.Charsets
import com.twitter.util._
import com.twitter.zipkin.common.Span

import scala.collection.JavaConversions

/**
 * The adaptive sampler optimizes sampling towards a global rate. This state
 * is maintained in ZooKeeper.
 *
 * {{{
 * object MyCollectorServer extends TwitterServer
 *   with ..
 *   with AdaptiveSampler {
 *
 *   // Sampling will adjust dynamically towards a target rate.
 *   override def spanStoreFilter = newAdaptiveSamplerFilter()
 * --snip--
 * }}}
 *
 */
trait AdaptiveSampler { self: App =>
  val asBasePath = flag(
    "zipkin.sampler.adaptive.basePath",
    "/com/twitter/zipkin/sampler/adaptive",
    "Base path in ZooKeeper for the sampler to use")

  val asUpdateFreq = flag(
    "zipkin.sampler.adaptive.updateFreq",
    30.seconds,
    "Frequency with which to update the sample rate; minimum is 1.second")

  val asWindowSize = flag(
    "zipkin.sampler.adaptive.windowSize",
    30.minutes,
    "Amount of request rate data to base sample rate on")

  val asSufficientWindowSize = flag(
    "zipkin.sampler.adaptive.sufficientWindowSize",
    10.minutes,
    "Amount of request rate data to gather before calculating sample rate")

  val asOutlierThreshold = flag(
    "zipkin.sampler.adaptive.outlierThreshold",
    5.minutes,
    "Amount of time to see outliers before updating sample rate")

  val zkServerLocations = flag(
    "zipkin.zookeeper.location",
    Seq(new InetSocketAddress(2181)),
    "Location of the ZooKeeper server")

  val zkServerCredentials = flag[String](
    "zipkin.zookeeper.credentials",
    "Optional credentials of the form 'username:password'")

  /** trace IDs greater than than this value will be dropped */
  val boundary = new AtomicLong(Long.MaxValue)
  /** Count of spans requested to be written to storage since last reset. */
  val spanCount = new AtomicInteger

  def newAdaptiveSamplerFilter(
    boundary: AtomicLong = boundary,
    spanCount: AtomicInteger = spanCount,
    basePath: String = asBasePath(),
    updateFreq: Duration = asUpdateFreq(),
    windowSize: Duration = asWindowSize(),
    sufficientWindowSize: Duration = asSufficientWindowSize(),
    outlierThreshold: Duration = asOutlierThreshold(),
    stats: StatsReceiver = DefaultStatsReceiver.scope("adaptiveSampler")
  ): Filter[Seq[Span], Unit, Seq[Span], Unit] = {
    val rate = new AdaptiveSampleRate(
      boundary,
      spanCount,
      zkServerLocations().map(isa => isa.getHostName + ":" + isa.getPort).mkString(","),
      zkServerCredentials.get.map(auth => ImmutableMap.of("digest", auth.getBytes(Charsets.Utf8)))
        .getOrElse(Collections.emptyMap()),
      basePath,
      updateFreq.inSeconds,
      windowSize.inSeconds,
      outlierThreshold.inSeconds,
      sufficientWindowSize.inSeconds
    )
    rate.stats = stats

    onExit {
      rate.close()
    }

    new SpanSamplerFilter(new Sampler(boundary, stats.scope("sampler")), spanCount, stats.scope("filter"))
  }
}
