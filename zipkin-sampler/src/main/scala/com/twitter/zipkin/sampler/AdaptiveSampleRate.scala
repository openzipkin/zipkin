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

import java.util.Collections.emptyMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}

import com.google.common.collect.EvictingQueue
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.twitter.util._
import com.twitter.zipkin.util.Util._
import org.apache.zookeeper.client.ConnectStringParser

import scala.reflect.ClassTag

/**
  * Dynamically adjusts a trace ID [[boundary]] derived from a target rate.
  *
  * [[boundary]] is used by storage samplers. Samplers accepts a percentage of trace ids by comparing
  * their absolute value against this boundary. eg `isSampled == abs(traceId) <= boundary`
  *
  * The caller is responsible for incrementing [[spanCount]] whenever a span is sent to storage.
  * [[AdaptiveSampleRate]] will watch [[spanCount]] value and feed it back into a global rate.
  * If feedback suggests traffic needs to be reduced, [[boundary]] will be lowered accordingly.
  *
  * @param boundary             Boundary to compare against trace IDs.
  *                             If rate is between 0.0 and 1.0, `boundary = Long.MAX_VALUE * rate`.
  *                             Samplers return true when spans are not debug and
  *                             `abs(traceId) <= boundary`
  * @param spanCount            The caller is responsible for incrementing [[spanCount]] whenever a
  *                             span is sent to storage.
  * @param zookeeperConnect     ZooKeeper is used to coordinate a global rate from members
  * @param zookeeperAuthInfo    Empty means don't authenticate. Otherwise key is the scheme and
  *                             value is the auth token.
  * @param zookeeperBasePath    Base path in ZooKeeper for the sampler to use
  * @param updateFreq           Frequency in seconds which to update the sample rate
  * @param windowSize           Seconds of request rate data to base sample rate on
  * @param sufficientWindowSize Seconds of request rate data to gather before calculating sample rate
  * @param outlierThreshold     Seconds to see outliers before updating sample rate
  */
class AdaptiveSampleRate(
  boundary: AtomicLong,
  spanCount: AtomicInteger,
  zookeeperConnect: String,
  zookeeperAuthInfo: java.util.Map[String, Array[Byte]] = emptyMap(), // map to avoid needing an option type
  zookeeperBasePath: String = "/com/twitter/zipkin/sampler/adaptive",
  updateFreq: Int = 30,
  windowSize: Int = 30.minutes.inSeconds,
  sufficientWindowSize: Int = 10.minutes.inSeconds,
  outlierThreshold: Int = 5.minutes.inSeconds
) extends java.io.Closeable {
  val zkCredentials = {
    checkArgument(zookeeperAuthInfo.size() <= 1, s"Only single ZooKeeper AuthInfo supported")
    if (zookeeperAuthInfo.isEmpty)
      ZooKeeperClient.Credentials.NONE
    else {
      val entry = zookeeperAuthInfo.entrySet().iterator().next()
      ZooKeeperClient.credentials(entry.getKey, entry.getValue)
    }
  }
  val zkClient = new ZKClient(new ConnectStringParser(zookeeperConnect).getServerAddresses(), zkCredentials)
  val electionPath = zookeeperBasePath + "/election"
  val storeRatePath = zookeeperBasePath + "/storeRates"
  val sampleRatePath = zookeeperBasePath + "/sampleRate"
  val targetStoreRatePath = zookeeperBasePath + "/targetStoreRate"
  val targetStoreRateWatch = zkClient.watchData(targetStoreRatePath)
  val targetStoreRate = targetStoreRateWatch.data.map(translateNode("targetStoreRate", 0, _.toInt))
  val log = Logger.get("adaptiveSampler")
  private[sampler] var stats: StatsReceiver = DefaultStatsReceiver.scope("adaptiveSampler")

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
  val sampleRateVar = sampleRateWatch.data.map(translateNode("sampleRate", 0.0f, _.toFloat))
  sampleRateVar.changes.map(sampleRate => {
    if (sampleRate < 0.0f || sampleRate > 1.0f) {
      log.warning(s"sampleRate should be between 0 and 1: was $sampleRate")
      boundary.get()
    } else {
      (Long.MaxValue * sampleRate).toLong
    }
  }).register(new Witness[Long] {
    def notify(t: Long): Unit = boundary.set(t)
  })
  stats.addGauge("rate") { sampleRateVar.sample() }

  val buffer = new AtomicRingBuffer[Int](windowSize / updateFreq)

  val isLeader = new IsLeaderCheck[Double](zkClient, electionPath, stats.scope("leaderCheck"), log)
  val cooldown = new CooldownCheck[Double](outlierThreshold, stats.scope("cooldownCheck"), log)

  stats.scope("flowReporter").addGauge("spanCount") { spanCount.get }

  /** Converts[[spanCount]] to a minutely rate based on the value of [[updateFreq]] */
  val updateTask = DefaultTimer.twitter.schedule(updateFreq.seconds) {
    storeRate.update((1.0 * spanCount.getAndSet(0) * 60 / updateFreq).toInt)
  }

  val calculator =
    { v: Int => Some(buffer.pushAndSnap(v)) } andThen
      new TargetStoreRateCheck[Seq[Int]](targetStoreRate, stats.scope("targetStoreRateCheck"), log) andThen
      new SufficientDataCheck[Int](sufficientWindowSize / updateFreq, stats.scope("sufficientDataCheck"), log) andThen
      new ValidDataCheck[Int](_ > 0, stats.scope("validDataCheck"), log) andThen
      new OutlierCheck(targetStoreRate, outlierThreshold / updateFreq, stats = stats.scope("outlierCheck"), log = log) andThen
      new CalculateSampleRate(targetStoreRate, sampleRateVar, stats = stats.scope("sampleRateCalculator"), log = log) andThen
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

  val closer = Closable.all(
    updateTask,
    targetStoreRateWatch,
    storeRateGroup,
    sampleRateWatch,
    isLeader,
    globalSampleRateUpdater,
    zkClient
  )

  /** Waits up to a second for a graceful shutdown. */
  override def close = try {
    Await.result(closer.close(1.second), 1.second)
  } catch {
    case NonFatal(e) => log.debug(e, "Failed to close AdaptiveSampleRate")
  }

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
  updateFreq: Int,
  calculate: (Int => Option[Double]),
  stats: StatsReceiver = DefaultStatsReceiver.scope("globalSampleRateUpdater"),
  log: Logger = Logger.get("GlobalSampleRateUpdater")
) extends Closable {
  private[this] val globalRateCounter = stats.counter("rate")

  private[this] val dataWatcher = zkClient.groupData(storeRatePath, updateFreq.seconds)
  dataWatcher.data.changes.register(Witness { vals =>
    val memberVals = vals map {
      case bytes: Array[Byte] =>
        try new String(bytes).toInt catch { case e: Exception => 0 }
      case _ => 0
    }

    val sum = memberVals.sum
    log.debug("global rate update: " + sum + " " + memberVals)
    globalRateCounter.incr((sum * 1.0 * updateFreq / 60).toInt)

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

class TargetStoreRateCheck[T](
  targetStoreRate: Var[Int],
  stats: StatsReceiver = DefaultStatsReceiver.scope("targetStoreRateCheck"),
  log: Logger = Logger.get("TargetStoreRateCheck")
) extends (Option[T] => Option[T]) {
  private[this] val currentTargetStoreRate = new AtomicInteger(0)
  targetStoreRate.changes.register(Witness(currentTargetStoreRate.set(_)))
  stats.addGauge("currentTargetStoreRate") { currentTargetStoreRate.get }

  private[this] val validCounter = stats.counter("valid")
  private[this] val invalidCounter = stats.counter("invalid")

  def apply(in: Option[T]): Option[T] =
    in filter { _ =>
      val valid = currentTargetStoreRate.get > 0
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
  periodSeconds: Int,
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
      if (allow) timer.doLater(periodSeconds.seconds) { permit.set(true) }
      allow
    }
}

class OutlierCheck(
  targetStoreRate: Var[Int],
  requiredDataPoints: Int,
  threshold: Double = 0.15,
  stats: StatsReceiver = DefaultStatsReceiver.scope("outlierCheck"),
  log: Logger = Logger.get("OutlierCheck")
) extends (Option[Seq[Int]] => Option[Seq[Int]]) {
  /** holds the current value of [[targetStoreRate]] */
  private[this] val currentTargetStoreRate = new AtomicInteger(0)
  targetStoreRate.changes.register(Witness(currentTargetStoreRate.set(_)))

  def apply(in: Option[Seq[Int]]): Option[Seq[Int]] =
    in filter { buf =>
      val outliers = buf.segmentLength(isOut(currentTargetStoreRate.get, _), buf.length - requiredDataPoints)
      log.debug("checking for outliers: " + outliers + " " + requiredDataPoints)
      outliers == requiredDataPoints
    }

  private[this] def isOut(targetRate: Int, rate: Int): Boolean =
    math.abs(rate - targetRate) > targetRate * threshold
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
  sampleRate: Var[Float],
  calculate: Seq[Int] => Double = DiscountedAverage,
  threshold: Double = 0.05,
  maxSampleRate: Double = 1.0,
  stats: StatsReceiver = DefaultStatsReceiver.scope("sampleRateCalculator"),
  log: Logger = Logger.get("CalculateSampleRate")
) extends (Option[Seq[Int]] => Option[Double]) {
  private[this] val currentTargetStoreRate = new AtomicInteger(0)
  targetStoreRate.changes.register(Witness(currentTargetStoreRate.set(_)))
  stats.addGauge("targetStoreRate") { currentTargetStoreRate.get }

  private[this] val currentSampleRate = new AtomicReference[Float](1.0f)
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
