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

import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.twitter.util._
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.SpanStore
import com.twitter.zipkin.zookeeper._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference, AtomicInteger}

trait AdaptiveSampler { self: App with ZooKeeperClientFactory =>
  val asBasePath = flag(
    "zipkin.sampler.adaptive.basePath",
    "/com/twitter/zipkin/sampler/adaptive",
    "Base path in ZooKeeper for the sampler to use")

  val asUpdateFreq = flag(
    "zipkin.sampler.adaptive.updateFreq",
    30.seconds,
    "Frequency with which to update the sample rate")

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

  def adaptiveSampleRateCalculator(
    targetReqRate: Var[Int],
    curReqRate: Var[Int],
    sampleRate: Var[Double],
    stats: StatsReceiver
  ): (Option[Seq[Int]] => Option[Double]) = {
    new RequestRateCheck[Seq[Int]](curReqRate, stats.scope("reqRateCheck")) andThen
    new SufficientDataCheck[Int](asSufficientWindowSize().inSeconds / asUpdateFreq().inSeconds, stats.scope("sufficientDataCheck")) andThen
    new ValidDataCheck[Int](_ > 0, stats.scope("validDataCheck")) andThen
    new OutlierCheck(curReqRate, asOutlierThreshold().inSeconds / asUpdateFreq().inSeconds, stats = stats.scope("outlierCheck")) andThen
    new CalculateSampleRate(targetReqRate, sampleRate, stats = stats.scope("sampleRateCalculator"))
  }

  def newAdaptiveSamplerFilter(
    electionPath: String = asBasePath() + "/election",
    reporterPath: String = asBasePath() + "/requestRates",
    sampleRatePath: String = asBasePath() + "/sampleRate",
    targetRequestRatePath: String = asBasePath() + "/targetRequestRate",
    stats: StatsReceiver = DefaultStatsReceiver.scope("adaptiveSampler"),
    log: Logger = Logger.get("adaptiveSampler")
  ): SpanStore.Filter = {
    def translateNode[T](name: String, default: T, f: String => T): Array[Byte] => T = { bytes => try {
      val str = new String(bytes)
      log.debug("node translator [%s] got \"%s\"".format(name, str))
      f(str)
    } catch {
      case e: Exception =>
        log.error(e, "node translator [%s] error".format(name))
        default
    } }

    val targetReqRateWatch = zkClient.watchData(targetRequestRatePath)
    val targetReqRate = targetReqRateWatch.data.map(translateNode("targetReqRate", 0, _.toInt))

    val curReqRate = Var[Int](0)
    val reportingGroup = zkClient.joinGroup(reporterPath, curReqRate.map(_.toString.getBytes))

    val smplRateWatch = zkClient.watchData(sampleRatePath)
    val smplRate = smplRateWatch.data.map(translateNode("smplRate", 0.0, _.toDouble))

    val buffer = new AtomicRingBuffer[Int](asWindowSize().inSeconds / asUpdateFreq().inSeconds)

    val isLeader = new IsLeaderCheck[Double](zkClient, electionPath, stats.scope("leaderCheck"))
    val cooldown = new CooldownCheck[Double](asOutlierThreshold(), stats.scope("cooldownCheck"))

    val calculator =
      { v: Int => Some(buffer.pushAndSnap(v)) } andThen
      adaptiveSampleRateCalculator(targetReqRate, curReqRate, smplRate, stats) andThen
      isLeader andThen
      cooldown

    val globalSampleRateUpdater = new GlobalSampleRateUpdater(
      zkClient,
      sampleRatePath,
      reporterPath,
      asUpdateFreq(),
      calculator,
      stats.scope("globalRateUpdater"))

    onExit {
      val closer = Closable.all(
        targetReqRateWatch,
        reportingGroup,
        smplRateWatch,
        isLeader,
        globalSampleRateUpdater)

      Await.ready(closer.close())
    }

    new SpanSamplerFilter(new Sampler(smplRate, stats.scope("sampler")), stats.scope("filter")) andThen
    new FlowReportingFilter(curReqRate.update(_), stats.scope("flowReporter"))
  }

}

class AtomicRingBuffer[T: ClassManifest](maxSize: Int) {
  private[this] val underlying = new RingBuffer[T](maxSize)

  def pushAndSnap(newVal: T): Seq[T] = synchronized {
    underlying += newVal
    underlying.reverse
  }
}

/**
 * A SpanStore Filter that updates `curVar` with the current
 * flow based every `freq`.
 */
class FlowReportingFilter(
  update: (Int => Unit),
  stats: StatsReceiver = DefaultStatsReceiver.scope("flowReporter"),
  freq: Duration = 30.seconds,
  timer: Timer = DefaultTimer.twitter
) extends SpanStore.Filter with Closable {
  private[this] val spanCount = new AtomicInteger
  private[this] val countGauge = stats.addGauge("spanCount") { spanCount.get }

  private[this] val updateTask = timer.schedule(freq) {
    update(spanCount.getAndSet(0))
  }

  def apply(spans: Seq[Span], store: Service[Seq[Span], Unit]): Future[Unit] = {
    spanCount.addAndGet(spans.size)
    store(spans)
  }

  def close(deadline: Time): Future[Unit] =
    updateTask.close(deadline)
}

/**
 * A filter that uses ZK leader election to decide which node should be allowed
 * to operate on the incoming value.
 */
class IsLeaderCheck[T](
  zkClient: ZKClient,
  electionPath: String,
  stats: StatsReceiver = DefaultStatsReceiver.scope("leaderCheck")
) extends (Option[T] => Option[T]) with Closable {
  private[this] val isLeader = new AtomicBoolean(false)
  private[this] val leadership = zkClient.offerLeadership(electionPath)
  leadership.data.changes.register(Witness(isLeader.set(_)))

  private[this] val isLeaderGauge = stats.addGauge("isLeader") { if (isLeader.get) 1 else 0 }

  def apply(in: Option[T]): Option[T] =
    in filter { _ => isLeader.get }

  def close(deadline: Time): Future[Unit] =
    leadership.close(deadline)
}

/**
 * Pulls group data from `reporterPath` every `updateFreq` and passes the sum to `calculate`. If
 * this instance is the current leader of `electionPath` and `calculate` returns Some(val) the
 * global sample rate at `sampleRatePath` will be updated.
 */
class GlobalSampleRateUpdater(
  zkClient: ZKClient,
  sampleRatePath: String,
  reporterPath: String,
  updateFreq: Duration,
  calculate: (Int => Option[Double]),
  stats: StatsReceiver = DefaultStatsReceiver.scope("globalSampleRateUpdater")
) extends Closable {
  private[this] val globalRateCounter = stats.counter("rate")

  private[this] val dataWatcher = zkClient.groupData(reporterPath, updateFreq)
  dataWatcher.data.changes.register(Witness { vals =>
    val memberVals = vals map {
      case bytes: Array[Byte] =>
        try new String(bytes).toInt catch { case e: Exception => 0 }
      case _ => 0
    }

    val sum = memberVals.sum
    globalRateCounter.incr(sum)

    calculate(sum) foreach { rate =>
      zkClient.setData(sampleRatePath, rate.toString.getBytes)
    }
  })

  def close(deadline: Time): Future[Unit] =
    dataWatcher.close(deadline)
}

class RequestRateCheck[T](
  reqRate: Var[Int],
  stats: StatsReceiver = DefaultStatsReceiver.scope("requestRateCheck")
) extends (Option[T] => Option[T]) {
  private[this] val curRate = new AtomicInteger(0)
  reqRate.changes.register(Witness(curRate.set(_)))

  private[this] val curRateGauge = stats.addGauge("curRate") { curRate.get }
  private[this] val validCounter = stats.counter("valid")
  private[this] val invalidCounter = stats.counter("invalid")

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      val valid = curRate.get > 0
      (if (valid) validCounter else invalidCounter).incr()
      valid
    }
}

class SufficientDataCheck[T](
  sufficientThreshold: Int,
  stats: StatsReceiver = DefaultStatsReceiver.scope("sufficientDataCheck")
) extends (Option[Seq[T]] => Option[Seq[T]]) {
  private[this] val sufficientCounter = stats.counter("sufficient")
  private[this] val insufficientCounter = stats.counter("insufficient")

  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { i =>
      val sufficient = i.length >= sufficientThreshold
      (if (sufficient) sufficientCounter else insufficientCounter).incr()
      sufficient
    }
}

class ValidDataCheck[T](
  validate: T => Boolean,
  stats: StatsReceiver = DefaultStatsReceiver.scope("validDataCheck")
) extends (Option[Seq[T]] => Option[Seq[T]]) {
  private[this] val validCounter = stats.counter("valid")
  private[this] val invalidCounter = stats.counter("invalid")

  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { i =>
      val valid = i.forall(validate)
      (if (valid) validCounter else invalidCounter).incr()
      valid
    }
}

class CooldownCheck[T](
  period: Duration,
  stats: StatsReceiver = DefaultStatsReceiver.scope("cooldownCheck"),
  timer: Timer = DefaultTimer.twitter
) extends (Option[T] => Option[T]) {
  private[this] val permit = new AtomicBoolean(true)
  private[this] val coolingGauge = stats.addGauge("cooling") { if (permit.get) 0 else 1 }

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      val allow = permit.compareAndSet(true, false)
      if (allow) timer.doLater(period) { permit.set(true) }
      allow
    }
}

class OutlierCheck(
  reqRate: Var[Int],
  requiredDataPoints: Int,
  threshold: Double = 0.15,
  stats: StatsReceiver = DefaultStatsReceiver.scope("outlierCheck")
) extends (Option[Seq[Int]] => Option[Seq[Int]]) {
  private[this] val curRate = new AtomicInteger(0)
  reqRate.changes.register(Witness(curRate.set(_)))

  def apply(in: Option[Seq[Int]]): Option[Seq[Int]] =
    in filter { buf =>
      buf.segmentLength(isOut(curRate.get, _), buf.length - requiredDataPoints) == requiredDataPoints
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
  targetRate: Var[Int],
  smplRate: Var[Double],
  calculate: Seq[Int] => Double = DiscountedAverage,
  threshold: Double = 0.05,
  maxSampleRate: Double = 1.0,
  stats: StatsReceiver = DefaultStatsReceiver.scope("sampleRateCalculator")
) extends (Option[Seq[Int]] => Option[Double]) {
  private[this] val tgtReqRate = new AtomicInteger(0)
  targetRate.changes.register(Witness(tgtReqRate.set(_)))

  private[this] val curSmplRate = new AtomicReference[Double](1.0)
  smplRate.changes.register(Witness(curSmplRate))

  private[this] val tgtRateGauge = stats.addGauge("targetReqRate") { tgtReqRate.get }
  private[this] val curlRateGauge = stats.addGauge("currentSampleRate") { curSmplRate.get.toFloat }

  /**
   * Since we assume that the sample rate and storage request rate are
   * linearly related by the following:
   *
   * newSampleRate / targetStoreRate = currentSampleRate / currentStoreRate
   * thus
   * newSampleRate = (currentSampleRate * targetStoreRate) / currentStoreRate
   */
  def apply(in: Option[Seq[Int]]): Option[Double] = in flatMap { vals =>
    val curStoreRate = calculate(vals)
    if (curStoreRate <= 0) None else {
      val curRateSnap = curSmplRate.get
      val newSampleRate = curRateSnap * tgtReqRate.get / curStoreRate
      val sr = math.min(maxSampleRate, newSampleRate)
      if (math.abs(curRateSnap - sr) < threshold) None else Some(sr)
    }
  }
}
