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
package com.twitter.zipkin.collector.sampler.adaptive

import com.twitter.common.base.ExceptionalCommand
import com.twitter.common.zookeeper.Group.JoinException
import com.twitter.common.zookeeper._
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{TimerTask, Duration}
import com.twitter.zipkin.collector.sampler.adaptive.policy.LeaderPolicy
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import scala.collection.JavaConverters._

/**
 * Adjusts the sample rate for the collector nodes depending on the traffic
 *
 * Keeps a window of values for the past M minutes.
 * - If the last reported total traffic is an outlier (outside a threshold around
 *   the target storage request rate) and have had more than _N_
 *   consecutive outliers, then adjust the sample rate.
 * - If its not an outlier for _L_ minutes, and the mean is outside the threshold,
 *   adjust the sample rate.
 */
trait ZooKeeperAdaptiveLeader extends Candidate.Leader {
  val log = Logger.get("adaptivesampler")
  val exceptionCounter      = Stats.getCounter("collector.sampler.adaptive.leader.exception")
  val CounterOutliers       = Stats.getCounter("collector.sampler.adaptive.leader.outliers")
  val CounterInliers        = Stats.getCounter("collector.sampler.adaptive.leader.inliers")
  val CounterOutliersAdjust = Stats.getCounter("collector.sampler.adaptive.leader.outliers.adjust")
  val CounterOutliersNop    = Stats.getCounter("collector.sampler.adaptive.leader.outliers.nop")
  val CounterInliersAdjust  = Stats.getCounter("collector.sampler.adaptive.leader.inliers.adjust")
  val CounterInliersNop     = Stats.getCounter("collector.sampler.adaptive.leader.inliers.nop")

  /** Config with timer, Zookeeper client, sampleRateConfig, storageRequestRateConfig */
  val config: ZooKeeperAdaptiveSamplerConfig

  /** Group for reporting */
  val reportGroup: Group

  /** Group for leader election */
  val leaderGroup: Group

  val bufferSize: Duration

  val pollInterval: Duration

  val leaderPolicy: LeaderPolicy[BoundedBuffer]

  @volatile var isLeader = false

  var timerTask: Option[TimerTask] = None

  lazy val buf: BoundedBuffer = new BoundedBuffer {
    val maxLength = bufferSize.inSeconds / pollInterval.inSeconds
  }

  @volatile var leaderSR = 0.0

  Stats.addGauge("collector.sampler.adaptive.leader.latest")             { buf.latest }

  def start() {
    log.info("ZKLeader: start")
    val candidateImpl: CandidateImpl = new CandidateImpl(leaderGroup)
    candidateImpl.offerLeadership(this)

    timerTask = Some {
      config.taskTimer.schedule(pollInterval) {
        try {
          update()
          if (isLeader) {
            lead()
          }
        } catch {
          case e: Exception =>
            exceptionCounter.incr()
            log.error(e, "ZKLeader: exception")
        }
      }
    }
  }

  def shutdown() {
    timerTask match {
      case Some(x) => x.cancel()
      case None =>
    }
  }

  def onElected(abdicate: ExceptionalCommand[JoinException]) {
    isLeader = true
    log.info("ZKLeader: elected")
  }

  def onDefeated() {
    log.info("ZKLeader: defeated")
    isLeader = false
  }

  private[adaptive] def update() {
    buf.update {
      val zk = config.client.get
      reportGroup.getMemberIds.asScala.map { id =>
        zk.getData(reportGroup.getMemberPath(id), true, null) match {
          case null => 0.0
          case b: Array[Byte] => {
            log.info(new String(b))
            new String(b).toDouble
          }
        }
      }.toSeq.sum
    }
  }

  private[adaptive] def lead() {
    leaderPolicy(Some(buf)) match {
      case Some(sr) => {
        log.info("ZKLeader: Setting sample rate: " + sr)
        config.sampleRate.set(sr)
        leaderPolicy.notifyChange(sr)
      }
      case None => {
        log.info("ZKLeader: Not changing sample rate")
      }
    }
  }
}

object ZooKeeperAdaptiveLeader {

  def apply(
    _config: ZooKeeperAdaptiveSamplerConfig,
    _reportGroup: Group,
    _leaderGroup: Group,
    _bufferSize: Duration,
    _pollInterval: Duration,
    _leaderPolicy: LeaderPolicy[BoundedBuffer]
  ): ZooKeeperAdaptiveLeader =
    new ZooKeeperAdaptiveLeader() {
      val config       = _config
      val reportGroup  = _reportGroup
      val leaderGroup  = _leaderGroup
      val bufferSize   = _bufferSize
      val pollInterval = _pollInterval
      val leaderPolicy = _leaderPolicy
    }

  /**
   * Truncate a number to 4 decimal places
   */
  def truncate(x: Double): Double = {
    (x * 1000).toInt.toDouble / 1000
  }
}
