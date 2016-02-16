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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import com.google.common.collect.EvictingQueue
import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Filter, Service}
import com.twitter.logging.Logger
import com.twitter.util._
import com.twitter.zipkin.common.Span

import scala.reflect.ClassTag

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
 *
 *   def main() {
 *
 *     // Adds endpoints to adjust the sample rate via http
 *     configureAdaptiveSamplerHttpApi()
 *
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

  val zkServerCredentials = flag(
    "zipkin.zookeeper.credentials",
    "[none]",
    "Optional credentials of the form 'username:password'")

  lazy val zkClient = {
    val creds = zkServerCredentials.get map { creds =>
      val Array(u, p) = creds.split(':')
      (u, p)
    }
    new ZKClient(zkServerLocations(), creds)
  }

  def newAdaptiveSamplerFilter(
    electionPath: String = asBasePath() + "/election",
    storeRatePath: String = asBasePath() + "/storeRates",
    sampleRatePath: String = asBasePath() + "/sampleRate",
    targetStoreRatePath: String = asBasePath() + "/targetStoreRate",
    updateFreq: Duration = asUpdateFreq(),
    windowSize: Duration = asWindowSize(),
    outlierThreshold: Duration = asOutlierThreshold(),
    sufficientWindowSize: Duration = asSufficientWindowSize(),
    stats: StatsReceiver = DefaultStatsReceiver.scope("adaptiveSampler"),
    log: Logger = Logger.get("adaptiveSampler")
  ): Filter[Seq[Span], Unit, Seq[Span], Unit] = {

    val result = new AdaptiveSamplerFilter(
      zkClient,
      electionPath,
      storeRatePath,
      sampleRatePath,
      targetStoreRatePath,
      updateFreq,
      windowSize,
      outlierThreshold,
      sufficientWindowSize,
      stats,
      log
    )

    onExit {
      Await.ready(result.close())
    }
    result
  }
}

// Extracted for testing
private[sampler] class AdaptiveSamplerFilter (
  zkClient: ZKClient,
  electionPath: String,
  storeRatePath: String,
  sampleRatePath: String,
  targetStoreRatePath: String,
  updateFreq: Duration,
  windowSize: Duration,
  outlierThreshold: Duration,
  sufficientWindowSize: Duration,
  stats: StatsReceiver,
  log: Logger
) extends Filter[Seq[Span], Unit, Seq[Span], Unit] with Closable {

  val targetStoreRateWatch = zkClient.watchData(targetStoreRatePath)
  val targetStoreRate = targetStoreRateWatch.data.map(translateNode("targetStoreRate", 0, _.toInt))

  /**
   * Count of spans requested to be written to storage per minute, in the
   * local process.
   *
   * <p>This is named [[storeRate]] as opposed to `storageRate` as this is
   * measured regardless of success.
   */
  val storeRate = Var[Int](0)
  /**
   * Reports the local [[storeRate]] to a zookeeper group.
   *
   * <p>Summing this group will get the aggregate write rate for all nodes.
   */
  val storeRateGroup = zkClient.joinGroup(storeRatePath, storeRate.map(_.toString.getBytes))

  val sampleRateWatch = zkClient.watchData(sampleRatePath)
  val sampleRate = sampleRateWatch.data.map(translateNode("sampleRate", 0.0, _.toDouble))

  val buffer = new AtomicRingBuffer[Int](windowSize.inSeconds / updateFreq.inSeconds)

  val isLeader = new IsLeaderCheck[Double](zkClient, electionPath, stats.scope("leaderCheck"), log)
  val cooldown = new CooldownCheck[Double](outlierThreshold, stats.scope("cooldownCheck"), log)

  /** Count of spans requested to be written to storage since last reset. */
  val spanCount = new AtomicInteger
  stats.scope("flowReporter").addGauge("spanCount") { spanCount.get }

  /** Converts[[spanCount]] to a minutely rate based on the value of [[updateFreq]] */
  val updateTask = DefaultTimer.twitter.schedule(updateFreq) {
    storeRate.update((1.0 * spanCount.getAndSet(0) * 60.seconds.inNanoseconds / updateFreq.inNanoseconds).toInt)
  }

  val calculator =
    { v: Int => Some(buffer.pushAndSnap(v)) } andThen
      new StoreRateCheck[Seq[Int]](storeRate, stats.scope("storeRateCheck"), log) andThen
      new SufficientDataCheck[Int](sufficientWindowSize.inSeconds / updateFreq.inSeconds, stats.scope("sufficientDataCheck"), log) andThen
      new ValidDataCheck[Int](_ > 0, stats.scope("validDataCheck"), log) andThen
      new OutlierCheck(storeRate, outlierThreshold.inSeconds / updateFreq.inSeconds, stats = stats.scope("outlierCheck"), log = log) andThen
      new CalculateSampleRate(targetStoreRate, sampleRate, stats = stats.scope("sampleRateCalculator"), log = log) andThen
      isLeader andThen
      cooldown

  val globalSampleRateUpdater = new GlobalSampleRateUpdater(
    zkClient,
    sampleRatePath,
    storeRatePath,
    updateFreq,
    calculator,
    stats.scope("globalRateUpdater"),
    log)

  val spanSampler = new SpanSamplerFilter(new Sampler(sampleRate, stats.scope("sampler")), stats.scope("filter"))

  /** Increments [[spanCount]] then stores spans retained by [[spanSampler]]. */
  override def apply(spans: Seq[Span], store: Service[Seq[Span], Unit]): Future[Unit] = {
    spanSampler.apply(spans, Service.mk(spans => {
      spanCount.addAndGet(spans.size)
      store(spans)
    }))
  }

  val closer = Closable.all(
    updateTask,
    targetStoreRateWatch,
    storeRateGroup,
    sampleRateWatch,
    isLeader,
    globalSampleRateUpdater)

  override def close(after: Duration): Future[Unit] = closer.close(after)
  override def close(deadline: Time): Future[Unit] = closer.close(deadline)

  def translateNode[T](name: String, default: T, f: String => T): Array[Byte] => T = { bytes =>
    if (bytes.length == 0) {
      log.debug("node translator [%s] defaulted to \"%s\"".format(name, default))
      default
    } else {
      val str = new String(bytes)
      log.debug("node translator [%s] got \"%s\"".format(name, str))
      try {
        f(str)
      } catch {
        case e: Exception =>
          log.error(e, "node translator [%s] error".format(name))
          default
      }
    }
  }
}

class AtomicRingBuffer[T: ClassTag](maxSize: Int) {
  private[this] val underlying: EvictingQueue[T] = EvictingQueue.create[T](maxSize)

  def pushAndSnap(newVal: T): Seq[T] = synchronized {
    underlying.add(newVal)
    val arr = underlying.toArray.asInstanceOf[Array[T]]
    arr.reverse
  }
}

/**
 * A filter that uses ZK leader election to decide which node should be allowed
 * to operate on the incoming value.
 */
class IsLeaderCheck[T](
  zkClient: ZKClient,
  electionPath: String,
  stats: StatsReceiver = DefaultStatsReceiver.scope("leaderCheck"),
  log: Logger = Logger.get("IsLeaderCheck")
) extends (Option[T] => Option[T]) with Closable {
  private[this] val isLeader = new AtomicBoolean(false)
  private[this] val leadership = zkClient.offerLeadership(electionPath)
  leadership.data.changes.register(Witness(isLeader.set(_)))

  private[this] val isLeaderGauge = stats.addGauge("isLeader") { if (isLeader.get) 1 else 0 }

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      log.debug("is leader check: " + isLeader.get)
      isLeader.get
    }

  def close(deadline: Time): Future[Unit] =
    leadership.close(deadline)
}

/**
 * Pulls group data from `storeRatePath` every `updateFreq` and passes the sum to `calculate`. If
 * this instance is the current leader of `electionPath` and `calculate` returns Some(val) the
 * global sample rate at `sampleRatePath` will be updated.
 */
class GlobalSampleRateUpdater(
  zkClient: ZKClient,
  sampleRatePath: String,
  storeRatePath: String,
  updateFreq: Duration,
  calculate: (Int => Option[Double]),
  stats: StatsReceiver = DefaultStatsReceiver.scope("globalSampleRateUpdater"),
  log: Logger = Logger.get("GlobalSampleRateUpdater")
) extends Closable {
  private[this] val globalRateCounter = stats.counter("rate")

  private[this] val dataWatcher = zkClient.groupData(storeRatePath, updateFreq)
  dataWatcher.data.changes.register(Witness { vals =>
    val memberVals = vals map {
      case bytes: Array[Byte] =>
        try new String(bytes).toInt catch { case e: Exception => 0 }
      case _ => 0
    }

    val sum = memberVals.sum
    log.debug("global rate update: " + sum + " " + memberVals)
    globalRateCounter.incr((sum * 1.0 * updateFreq.inNanoseconds / 60.seconds.inNanoseconds).toInt)

    calculate(sum) foreach { rate =>
      log.debug("setting new sample rate: " + sampleRatePath + " " + rate)
      zkClient.setData(sampleRatePath, rate.toString.getBytes) onFailure { cause =>
        log.error(cause, s"could not set sample rate to $rate for $sampleRatePath")
      }
    }
  })

  def close(deadline: Time): Future[Unit] =
    dataWatcher.close(deadline)
}

class StoreRateCheck[T](
  storeRate: Var[Int],
  stats: StatsReceiver = DefaultStatsReceiver.scope("storeRateCheck"),
  log: Logger = Logger.get("StoreRateCheck")
) extends (Option[T] => Option[T]) {
  private[this] val currentStoreRate = new AtomicInteger(0)
  storeRate.changes.register(Witness(currentStoreRate.set(_)))
  stats.addGauge("currentStoreRate") { currentStoreRate.get }

  private[this] val validCounter = stats.counter("valid")
  private[this] val invalidCounter = stats.counter("invalid")

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      val valid = currentStoreRate.get > 0
      (if (valid) validCounter else invalidCounter).incr()
      valid
    }
}

class SufficientDataCheck[T](
  sufficientThreshold: Int,
  stats: StatsReceiver = DefaultStatsReceiver.scope("sufficientDataCheck"),
  log: Logger = Logger.get("SufficientDataCheck")
) extends (Option[Seq[T]] => Option[Seq[T]]) {
  private[this] val sufficientCounter = stats.counter("sufficient")
  private[this] val insufficientCounter = stats.counter("insufficient")

  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { i =>
      val sufficient = i.length >= sufficientThreshold
      log.debug("checking for sufficient data: " + sufficient + " |  " + i.length + " | " + sufficientThreshold)
      (if (sufficient) sufficientCounter else insufficientCounter).incr()
      sufficient
    }
}

class ValidDataCheck[T](
  validate: T => Boolean,
  stats: StatsReceiver = DefaultStatsReceiver.scope("validDataCheck"),
  log: Logger = Logger.get("ValidDataCheck")
) extends (Option[Seq[T]] => Option[Seq[T]]) {
  private[this] val validCounter = stats.counter("valid")
  private[this] val invalidCounter = stats.counter("invalid")

  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { i =>
      val valid = i.forall(validate)
      log.debug("validating data: " + valid + " | " + i)
      (if (valid) validCounter else invalidCounter).incr()
      valid
    }
}

class CooldownCheck[T](
  period: Duration,
  stats: StatsReceiver = DefaultStatsReceiver.scope("cooldownCheck"),
  log: Logger = Logger.get("CooldownCheck"),
  timer: Timer = DefaultTimer.twitter
) extends (Option[T] => Option[T]) {
  private[this] val permit = new AtomicBoolean(true)
  private[this] val coolingGauge = stats.addGauge("cooling") { if (permit.get) 0 else 1 }

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      val allow = permit.compareAndSet(true, false)
      log.debug("checking cooldown: " + allow)
      if (allow) timer.doLater(period) { permit.set(true) }
      allow
    }
}

class OutlierCheck(
  storeRate: Var[Int],
  requiredDataPoints: Int,
  threshold: Double = 0.15,
  stats: StatsReceiver = DefaultStatsReceiver.scope("outlierCheck"),
  log: Logger = Logger.get("OutlierCheck")
) extends (Option[Seq[Int]] => Option[Seq[Int]]) {
  /** holds the current value of [[storeRate]] */
  private[this] val currentStoreRate = new AtomicInteger(0)
  storeRate.changes.register(Witness(currentStoreRate.set(_)))

  def apply(in: Option[Seq[Int]]): Option[Seq[Int]] =
    in filter { buf =>
      val outliers = buf.segmentLength(isOut(currentStoreRate.get, _), buf.length - requiredDataPoints)
      log.debug("checking for outliers: " + outliers + " " + requiredDataPoints)
      outliers == requiredDataPoints
    }

  private[this] def isOut(curRate: Int, datum: Int): Boolean =
    math.abs(datum - curRate) > curRate * threshold
}

object DiscountedAverage extends (Seq[Int] => Double) {
  def apply(vals: Seq[Int]): Double =
    calculate(vals, 0.9)

  def calculate(vals: Seq[Int], discount: Double): Double = {
    val discountTotal = (0 until vals.length).map(math.pow(discount, _)).sum
    vals.zipWithIndex.map { case (e, i) => math.pow(discount, i) * e }.sum / discountTotal
  }

  def truncate(x: Double): Double = (x * 1000).toInt.toDouble / 1000
}

class CalculateSampleRate(
  targetStoreRate: Var[Int],
  sampleRate: Var[Double],
  calculate: Seq[Int] => Double = DiscountedAverage,
  threshold: Double = 0.05,
  maxSampleRate: Double = 1.0,
  stats: StatsReceiver = DefaultStatsReceiver.scope("sampleRateCalculator"),
  log: Logger = Logger.get("CalculateSampleRate")
) extends (Option[Seq[Int]] => Option[Double]) {
  private[this] val currentTargetStoreRate = new AtomicInteger(0)
  targetStoreRate.changes.register(Witness(currentTargetStoreRate.set(_)))
  stats.addGauge("targetStoreRate") { currentTargetStoreRate.get }

  private[this] val currentSampleRate = new AtomicReference[Double](1.0)
  sampleRate.changes.register(Witness(currentSampleRate))
  stats.addGauge("currentSampleRate") { currentSampleRate.get.toFloat }

  private[this] val currentStoreRate = new AtomicInteger(0)
  stats.addGauge("currentStoreRate") { currentStoreRate.get }

  /**
   * Since we assume that the sample rate and storage request rate are
   * linearly related by the following:
   *
   * newSampleRate / targetStoreRate = currentSampleRate / currentStoreRate
   * thus
   * newSampleRate = (currentSampleRate * targetStoreRate) / currentStoreRate
   */
  def apply(in: Option[Seq[Int]]): Option[Double] = {
    log.debug("Calculating rate for: " + in)
    in flatMap { vals =>
      val calculatedStoreRate = calculate(vals)
      currentStoreRate.set(calculatedStoreRate.toInt)
      log.debug("Calculated store rate: " + calculatedStoreRate)
      if (calculatedStoreRate <= 0) None else {
        val oldSampleRate = currentSampleRate.get
        val newSampleRate = oldSampleRate * currentTargetStoreRate.get / calculatedStoreRate
        val sampleRate = math.min(maxSampleRate, newSampleRate)
        val change = math.abs(oldSampleRate - sampleRate) / oldSampleRate
        log.debug(s"current store rate : $calculatedStoreRate ; current sample rate : $oldSampleRate ; target store rate : $currentTargetStoreRate")
        log.debug(s"sample rate : old $oldSampleRate ; new : $sampleRate ; threshold : $threshold ; execute : ${change >= threshold} ; % change : ${100*change}")
        if (change >= threshold) Some(sampleRate) else None
      }
    }
  }
}
