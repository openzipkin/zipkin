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

import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.common.zookeeper.{ZooKeeperUtils, Group}
import com.twitter.ostrich.stats.{Stats, Counter}
import com.twitter.logging.Logger
import com.twitter.util.{TimerTask, Duration}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.ZooDefs.Ids

/**
 * Reports a metric on this node to a ZooKeeper Group for consumption by the Leader
 */
trait ZooKeeperAdaptiveReporter {
  val log = Logger.get("adaptivesampler")
  val exceptionCounter = Stats.getCounter("zkreporter.exception")

  val config: ZooKeeperAdaptiveSamplerConfig

  /** Report group membership */
  val gm: Group.Membership

  /** Ostrich counter to report */
  val counter: Counter

  /** Frequency to update local window */
  val updateInterval: Duration

  /** Frequency to report to ZK */
  val reportInterval: Duration

  /** Amount of data to keep in local data structures */
  val reportWindow: Duration

  lazy val sliding: SlidingWindowCounter = new SlidingWindowCounter(counter, reportWindow.inSeconds / updateInterval.inSeconds)
  var timerTasks: Seq[TimerTask] = Seq()

  private[adaptive] var memberId = "default"

  def start() {
    log.info("ZKReporter: start")

    memberId = gm.getMemberId

    // Schedule the update task
    timerTasks :+ config.taskTimer.schedule(updateInterval) {
      update()
    }

    // Schedule the report task
    timerTasks :+ config.taskTimer.schedule(reportInterval) {
      report()
    }
  }

  def shutdown() {
    timerTasks.foreach {
      _.cancel()
    }
  }

  private[adaptive] def update() {
    sliding.update()
  }

  private[adaptive] def report() {
    try {
      val zk = config.client.get

      // If the member id has changed since the last run, delete the old node
      val currentId = gm.getMemberId
      if (memberId != currentId) {
        try {
          log.info("ZKReporter: deleting old node: " + memberId)
          zk.delete(path(memberId), ZooKeeperUtils.ANY_VERSION)
        } catch {
          case e: Exception => log.info(e, "ZKReporter: delete failed")
        } finally {
          memberId = currentId
        }
      }
      // We need to get the correct ZK data every time in case of connection loss
      if (zk.exists(path(currentId), false) == null) {
        zk.create(path(currentId), "0".getBytes, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
      }

      log.info("ZKReporter: reporting to zk: " + sliding())
      zk.setData(path(currentId), sliding().toString.getBytes, ZooKeeperUtils.ANY_VERSION)
    } catch {
      case e: Exception =>
        exceptionCounter.incr()
        log.error(e, "ZKReporter exception")
    }
  }

  private[this] def path(memberId: String) = config.reportPath + "/" + memberId
}

object ZooKeeperAdaptiveReporter {
  /** Build a ZooKeeperAdaptiveReporter */
  def apply(
    _config: ZooKeeperAdaptiveSamplerConfig,
    _gm: Group.Membership,
    _counter: Counter,
    _updateInterval: Duration,
    _reportInterval: Duration,
    _reportWindow: Duration
  ): ZooKeeperAdaptiveReporter =
    new ZooKeeperAdaptiveReporter() {
      val config         = _config
      val gm             = _gm
      val counter        = _counter
      val updateInterval = _updateInterval
      val reportInterval = _reportInterval
      val reportWindow   = _reportWindow
    }
}
