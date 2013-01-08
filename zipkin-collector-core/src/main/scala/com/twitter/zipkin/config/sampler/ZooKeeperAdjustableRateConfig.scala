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
package com.twitter.zipkin.config.sampler

import com.twitter.zipkin.gen.AdjustableRateException
import com.twitter.ostrich.stats.Stats
import com.twitter.logging.Logger
import com.twitter.zk._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.Watcher.Event

object ZooKeeperSampleRateConfig {

  val Min     = 0.0
  val Max     = 1.0
  val Default = 0.01

  def apply(client: ZkClient, configPath: String, key: String) =
    BoundedZooKeeperAdjustableRateConfig(client, configPath, key, Min, Max, Default)
}

object ZooKeeperStorageRequestRateConfig {

  /**
   *  Default value of 300 000 storage requests a minute
   *  Used by AdaptiveSampler to determine how much it should change the sample rate
   **/
  val Default = 300000

  def apply(client: ZkClient, configPath: String, key: String) = {
    ZooKeeperAdjustableRateConfig(client, configPath, key, Default)
  }
}

object BoundedZooKeeperAdjustableRateConfig {
  def apply(
    client: ZkClient,
    configPath: String,
    key: String,
    min: Double,
    max: Double,
    default: Double
  ): ZooKeeperAdjustableRateConfig = {

    new ZooKeeperAdjustableRateConfig(client, configPath, key, default) {
      override def validRate(rate: Double): Boolean = {
        rate >= min && rate <= max
      }
    }.initialize()
  }
}

object ZooKeeperAdjustableRateConfig {
  def apply(
    client: ZkClient,
    configPath: String,
    key: String,
    default: Double
  ): ZooKeeperAdjustableRateConfig = {

    new ZooKeeperAdjustableRateConfig(
      client,
      configPath,
      key,
      default
    ).initialize()
  }
}

/**
 * Sets and gets adjustable config values (Doubles) in ZooKeeper
 */
class ZooKeeperAdjustableRateConfig(
  client: ZkClient,
  configPath: String,
  key: String,
  default: Double
) extends AdjustableRateConfig {

  val log = Logger.get

  val defaultCounter = Stats.getCounter("zk_adjustable_rate_config_" + key)
  Stats.addGauge("zk_adjustable_rate_" + key) { get }

  lazy val zNode = client(path)

  @volatile private[this] var value: Double = default

  private[sampler] def monitor() {
    zNode.getData.monitor().foreach {
      _.foreach { data =>
        try {
          value = new String(data.bytes).toDouble
          log.info("Value changed: %s %f", key, value)
        } catch {
          case e: Exception => log.info("Bad byte array: " + data)
        }
      }
    }
  }

  private[sampler] def monitorSession() {
    // Re-monitor when we have a session expiration event
    client.onSessionEvent {
      case e =>
        if (e.eventType == Event.EventType.None && e.state == Event.KeeperState.Expired) {
          log.info("Session expiration encountered, re-monitoring")
          monitor()
        }
    }
  }

  private[sampler] def ensurePath(nodePath: String, data: Option[Array[Byte]]) {
    val index = nodePath.lastIndexOf('/')
    if (index > 0) {
      ensurePath(nodePath.substring(0, index), None)
    }

    try {
      data match {
        case Some(d) => client(nodePath).create(d).apply()
        case None => client(nodePath).create(null).apply()
      }
    } catch {
      case e: KeeperException.NodeExistsException => log.info("Node exists: " + nodePath)
    }
  }

  private[sampler] def initialize() = {
    try {
      zNode.create(default.toString.getBytes).apply()
    } catch {
      case e: KeeperException.NoNodeException =>
        /* Parent path does not exist */
        ensurePath(path, Some(default.toString.getBytes))
      case e: KeeperException.NodeExistsException => log.info("Node exists: " + path)
    }
    monitor()
    monitorSession()
    this
  }

  def get: Double = value

  def set(rate: Double) {
    if (!validRate(rate)) {
      throw AdjustableRateException("Invalid rate: " + rate)
    }

    zNode.setData(rate.toString.getBytes, -1)
  }

  def validRate(rate: Double): Boolean = true

  private def path = configPath + "/" + key
}
