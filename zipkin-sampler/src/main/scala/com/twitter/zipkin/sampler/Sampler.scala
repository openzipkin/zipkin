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

import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.{Var, Witness}
import java.util.concurrent.atomic.AtomicReference

/**
 * A sampler is essentially a function from Long => Boolean. Using a provided Var[Double] the sampler
 * will calculate whether the provided value should be sampled or not.
 */
class Sampler(
  rateVar: Var[Double],
  stats: StatsReceiver = DefaultStatsReceiver.scope("Sampler")
) extends (Long => Boolean) {
  private[this] val rate = new AtomicReference[Double](1.0)
  rateVar.changes.register(Witness(rate))
  stats.addGauge("rate") { rate.get.toFloat }

  private[this] val allowedCounter = stats.counter("allowed")
  private[this] val deniedCounter = stats.counter("denied")
  private[this] val zerosCounter = stats.counter("zeros")

  def apply(traceId: Long): Boolean = {
    val curRate = rate.get

    val allow = (curRate == 1) || {
      val t = if (traceId == Long.MinValue) Long.MaxValue else math.abs(traceId)
      if (t == 0) zerosCounter.incr()
      t > Long.MaxValue * (1 - curRate)
    }
    if (allow) allowedCounter.incr() else deniedCounter.incr()
    allow
  }
}
