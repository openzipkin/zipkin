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

import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util._
import com.twitter.zipkin.zookeeper._
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.SpanStore
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference, AtomicInteger}
import com.twitter.logging.Logger
import com.twitter.app.App
import com.twitter.zipkin.common.Span

trait AdaptiveSampler { self: App with ZooKeeperClientFactory =>
  private[this] val basePath = "/com/twitter/zipkin/adaptiveSampler"

  val asElectionPath =
    flag("zipkin.sampler.adaptive.electionPath", basePath + "/election", "ZK path to use for running leader elections")

  val asReporterPath =
    flag("zipkin.sampler.adaptive.reporterPath", basePath + "/requestRate", "ZK path to report current request rate to")

  val asSampleRatePath =
    flag("zipkin.sampler.adaptive.sampleRatePath", basePath + "/sampleRate", "ZK path to use for setting/getting the overall sampler rate")

  val asTargetRatePath =
    flag("zipkin.sampler.adaptive.targetRatePath", basePath + "/targetRate", "ZK path to use for getting the target store rate")

  val asUpdateFreq =
    flag("zipkin.sampler.adaptive.updateFreq", 30.seconds, "Frequency with which to update the sample rate")

  val asWindowSize =
    flag("zipkin.sampler.adaptive.windowSize", 30.minutes, "Amount of request rate data to base sample rate on")

  val asSufficientWindowSize =
    flag("zipkin.sampler.adaptive.sufficientWindowSize", 10.minutes, "Amount of request rate data to gather before calculating sample rate")

  val asOutlierThreshold =
    flag("zipkin.sampler.adaptive.outlierThreshold", 5.minutes, "Amount of time to see outliers before updating sample rate")

  val asDefault =
    flag("zipkin.sampler.adaptive.default", 1.0, "Default sample rate")

  def adaptiveSampleRateCalculator(
    targetReqRate: Var[Int],
    curReqRate: Var[Int],
    sampleRate: Var[Double]
  ): (Option[Seq[Int]] => Option[Double]) = {
    new RequestRateCheck[Seq[Int]](curReqRate) andThen
    new SufficientDataCheck[Int](asSufficientWindowSize().inSeconds / asUpdateFreq().inSeconds) andThen
    new ValidDataCheck[Int](_ > 0) andThen
    new OutlierCheck(curReqRate, asOutlierThreshold().inSeconds / asUpdateFreq().inSeconds) andThen
    new CalculateSampleRate(targetReqRate, sampleRate)
  }

  def newAdaptiveSamplerFilter(): SpanStore.Filter = {
    val targetReqRateWatch = zkClient.watchData(asTargetRatePath())
    val targetReqRate = targetReqRateWatch.data.map { bytes =>
      try new String(bytes).toInt catch { case e: Exception => 0 }
    }

    val curReqRate = Var[Int](0)
    val reportingGroup = zkClient.joinGroup(asReporterPath(), curReqRate.map(_.toString.getBytes))

    val smplRateWatch = zkClient.watchData(asSampleRatePath())
    val smplRate = smplRateWatch.data.map { bytes =>
      try new String(bytes).toDouble catch { case e: Exception => 0.0 }
    }

    val buffer = new BoundedBuffer[Int](asWindowSize().inSeconds / asUpdateFreq().inSeconds)

    val isLeader = new IsLeaderCheck[Double](zkClient, asElectionPath())
    val cooldown = new CooldownCheck[Double](asOutlierThreshold())

    val calculator =
      { v: Int => Some(buffer.pushAndSnap(v)) } andThen
      adaptiveSampleRateCalculator(targetReqRate, curReqRate, smplRate) andThen
      isLeader andThen
      cooldown

    val globalSampleRateUpdater = new GlobalSampleRateUpdater(
      zkClient,
      asSampleRatePath(),
      asReporterPath(),
      asUpdateFreq(),
      calculator
    )

    onExit {
      val closer = Closable.all(
        targetReqRateWatch,
        reportingGroup,
        smplRateWatch,
        isLeader,
        globalSampleRateUpdater)

      Await.ready(closer.close())
    }

    new SpanSamplerFilter(new Sampler(smplRate)) andThen
    new FlowReportingFilter(curReqRate.update(_))
  }

}

/**
 * A SpanStore Filter that updates `curVar` with the current
 * flow based every `freq`.
 */
class FlowReportingFilter(
  update: (Int => Unit),
  freq: Duration = 30.seconds,
  timer: Timer = DefaultTimer.twitter
) extends SpanStore.Filter with Closable {
  private[this] val spanCount = new AtomicInteger

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

class IsLeaderCheck[T](
  zkClient: ZKClient,
  electionPath: String
) extends (Option[T] => Option[T]) with Closable {
  private[this] val isLeader = new AtomicBoolean(false)
  private[this] val leadership = zkClient.offerLeadership(electionPath)
  leadership.data.changes.register(Witness(isLeader.set(_)))

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
  calculate: (Int => Option[Double])
) extends Closable {
  private[this] val dataWatcher = zkClient.groupData(reporterPath, updateFreq)
  dataWatcher.data.changes.register(Witness { vals =>
    val memberVals = vals map {
      case bytes: Array[Byte] =>
        try new String(bytes).toInt catch { case e: Exception => 0 }
      case _ => 0
    }

    calculate(memberVals.sum) foreach { rate =>
      zkClient.setData(sampleRatePath, rate.toString.getBytes)
    }
  })

  def close(deadline: Time): Future[Unit] =
    dataWatcher.close(deadline)
}

class RequestRateCheck[T](reqRate: Var[Int]) extends (Option[T] => Option[T]) {
  private[this] val curRate = new AtomicInteger(0)
  reqRate.changes.register(Witness(curRate.set(_)))

  def apply(in: Option[T]): Option[T] =
    in filter { _ => curRate.get > 0 }
}

class SufficientDataCheck[T](sufficient: Int) extends (Option[Seq[T]] => Option[Seq[T]]) {
  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { _.length >= sufficient }
}

class ValidDataCheck[T](validate: T => Boolean) extends (Option[Seq[T]] => Option[Seq[T]]) {
  def apply(in: Option[Seq[T]]): Option[Seq[T]] =
    in filter { _.forall(validate)  }
}

class CooldownCheck[T](
  period: Duration,
  timer: Timer = DefaultTimer.twitter
) extends (Option[T] => Option[T]) {
  private[this] val permit = new AtomicBoolean(true)

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
  threshold: Double = 0.15
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
  maxSampleRate: Double = 1.0
) extends (Option[Seq[Int]] => Option[Double]) {
  private[this] val tgtReqRate = new AtomicInteger(0)
  targetRate.changes.register(Witness(tgtReqRate.set(_)))

  private[this] val curSmplRate = new AtomicReference[Double](0.0)
  smplRate.changes.register(Witness(curSmplRate.set(_)))

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
