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

import com.twitter.logging.Logger
import com.twitter.util.{Timer, Duration}
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.zipkin.collector.sampler.adaptive.BoundedBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A PolicyFilter acts as a transformer of data that as passed to a LeaderPolicy.
 *
 * If a PolicyFilter returns None, it has decided for all downstream PolicyFilters
 * and LeaderPolicy that the sample rate should not be changed for this iteration.
 * Otherwise, it will continue to pass the data.
 */
trait PolicyFilter[T] {

  val log = Logger.get(getClass.getName)

  def andThen(next: PolicyFilter[T]): PolicyFilter[T] =
    new PolicyFilter[T] {
      override def apply(value: Option[T], policy: LeaderPolicy[T]): Option[Double] = {
        PolicyFilter.this.apply(value, new LeaderPolicy[T] {
          override def apply(value: Option[T]) = next(value, policy)
          def apply(value: T): Option[Double] = next(value) flatMap { policy(_) }
        })
      }

      def apply(value: T): Option[T] = PolicyFilter.this.apply(value) flatMap {next(_)}

      override def notifyChange(sampleRate: Double) {
        PolicyFilter.this.change(sampleRate)
        next.notifyChange(sampleRate)
      }
    }

  def andThen(policy: LeaderPolicy[T]): LeaderPolicy[T] = new LeaderPolicy[T] {
    override def apply(value: Option[T]) = PolicyFilter.this.apply(value, policy)
    def apply(value: T): Option[Double] = PolicyFilter.this.apply(value) flatMap { policy(_) }

    override def notifyChange(sampleRate: Double) {
      PolicyFilter.this.notifyChange(sampleRate)
      policy.notifyChange(sampleRate)
    }
  }

  def apply(value: Option[T], policy: LeaderPolicy[T]): Option[Double] =
    value flatMap { t: T => policy(apply(t)) }

  def apply(value: T): Option[T]

  /* Notifies Filter of a sample rate change. Useful for stateful filters */
  def notifyChange(sampleRate: Double) { change(sampleRate) }

  def change(sampleRate: Double) {}
}

/**
 * Filter to ensure a valid storage request rate. As a fail-safe the storage request rate
 * can be set to an invalid value to disable the adaptive sampler.
 * @param config
 */
class StorageRequestRateFilter(
  config: ZooKeeperAdaptiveSamplerConfig
) extends PolicyFilter[BoundedBuffer] {

  def storageRequestRate = config.storageRequestRate.get

  def apply(buf: BoundedBuffer): Option[BoundedBuffer] =
    if (storageRequestRate > 0) {
      Some(buf)
    } else {
      log.info("Invalid rate")
      None
    }
}

/**
 * Filter to ensure the adaptive sampler only acts on positive values
 */
class ValidLatestValueFilter extends PolicyFilter[BoundedBuffer] {

  def apply(buf: BoundedBuffer): Option[BoundedBuffer] = {
    if (buf.latest > 0) {
      Some(buf)
    } else {
      log.info("Invalid latest value")
      None
    }
  }
}

/**
 * Filter to ensure sufficient data has been gathered. Useful after a cold start when data may
 * be skewed due to start up noise.
 * @param dataSufficient
 * @param pollInterval
 */
class SufficientDataFilter(dataSufficient: Duration, pollInterval: Duration) extends PolicyFilter[BoundedBuffer] {

  lazy val sufficient = dataSufficient.inSeconds / pollInterval.inSeconds

  def apply(buf: BoundedBuffer): Option[BoundedBuffer] =
    if (buf.length >= sufficient) {
      Some(buf)
    } else {
      log.info("Insufficient data")
      None
    }
}

/**
 * Filter to ensure that the sample rate is only changed if we encounter a specified number
 * of outliers
 * @param config
 * @param threshold
 * @param outlierInterval
 * @param pollInterval
 */
class OutlierFilter(
  config: ZooKeeperAdaptiveSamplerConfig,
  threshold: Double,
  outlierInterval: Duration,
  pollInterval: Duration
) extends PolicyFilter[BoundedBuffer] {

  private[this] lazy val numValues = outlierInterval.inSeconds / pollInterval.inSeconds

  def apply(buf: BoundedBuffer): Option[BoundedBuffer] =
    if (buf.underlying.segmentLength(outsideThreshold(_), buf.length - numValues) == numValues) {
      Some(buf)
    } else {
      log.info("Not enough outliers")
      None
    }

  private def storageRequestRate = config.storageRequestRate.get

  private def outsideThreshold(latestValue: Double): Boolean =
    math.abs(latestValue - storageRequestRate) > storageRequestRate * threshold
}

/**
 * Filter to ensure the sample rate does not change too frequently
 * @param timeout
 * @param timer
 */
class CooldownFilter(timeout: Duration, implicit val timer: Timer) extends PolicyFilter[BoundedBuffer] {

  @volatile var allow: AtomicBoolean = new AtomicBoolean(true)

  def apply(buf: BoundedBuffer): Option[BoundedBuffer] =
    if (allow.get()) {
      Some(buf)
    } else {
      log.info("Not cooled down")
      None
    }

  override def change(sampleRate: Double) {
    if (allow.compareAndSet(true, false)) {
      timer.doLater(timeout) {
        allow.set(true)
      }
    }
  }
}
