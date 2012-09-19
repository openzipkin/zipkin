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
package com.twitter.zipkin.collector.sampler.adaptive.policy

import com.twitter.zipkin.collector.sampler.adaptive.BoundedBuffer
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.zipkin.config.sampler.ZooKeeperSampleRateConfig

/**
 * A LeaderPolicy decides on a new sample rate given some data.
 *
 * If a LeaderPolicy returns None, it has decided that the sample rate should not be changed.
 * Otherwise, the value in the returned Option is the new sample rate.
 * @tparam T
 */
trait LeaderPolicy[T] {

  def apply(value: Option[T]): Option[Double] =
    value flatMap { apply(_) }

  def apply(value: T): Option[Double]

  def notifyChange(sampleRate: Double) {}
}

class FailLeaderPolicy[T,U] extends LeaderPolicy[T] {
  def apply(value: T): Option[Double] = None
}

class PassLeaderPolicy[T](default: Double) extends LeaderPolicy[T] {
  def apply(value: T): Option[Double] = Some(default)
}

/**
 * Policy that given the most recent minutely storage request counts, computes a discounted
 * average of that data and a new sample rate from that value.
 */
trait DiscountedAverageLeaderPolicy extends LeaderPolicy[BoundedBuffer] {

  val config: ZooKeeperAdaptiveSamplerConfig

  val sampleRateThreshold: Double

  private def storageRequestRate = config.storageRequestRate.get

  def apply(buf: BoundedBuffer): Option[Double] = {
    val newAvg = discountedAvg(buf)
    val currentSR = config.sampleRate.get
    if (newAvg <= 0) {
      None
    } else {
      /**
       * Since we assume that the sample rate and storage request rate are
       * linearly related by the following:
       *
       * currentSR / currentStorageRequestRate = newSR / targetStorageRequestRate
       *
       * We solve for the new sample rate
       */
      val newSR = currentSR * storageRequestRate / newAvg
      val sr = math.min(ZooKeeperSampleRateConfig.Max, truncate(newSR))
      if (math.abs(currentSR - sr) < sampleRateThreshold) {
        None
      } else {
        Some(sr)
      }
    }
  }

  def discountedAvg(buf: BoundedBuffer): Double = discountedAvg(buf, 0.9)

  def discountedAvg(buf: BoundedBuffer, discount: Double): Double = {
    val underlying = buf.underlying
    val discountTotal =
      (0 until underlying.length).map {
        math.pow(discount, _)
      }.sum


    underlying.zipWithIndex.map {
      case (e, i) => math.pow(discount, i) * e
    }.sum / discountTotal
  }

  private def truncate(x: Double): Double = (x * 1000).toInt.toDouble / 1000
}

object DiscountedAverageLeaderPolicy {
  def apply(
    _config: ZooKeeperAdaptiveSamplerConfig,
    _sampleRateThreshold: Double
  ): DiscountedAverageLeaderPolicy = new DiscountedAverageLeaderPolicy {
    val config = _config
    val sampleRateThreshold = _sampleRateThreshold
  }
}
