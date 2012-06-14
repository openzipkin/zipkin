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
package com.twitter.zipkin.collector

import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{StatsReceiver, NullStatsReceiver}
import com.twitter.logging.Logger
import com.twitter.util.Timer
import org.apache.zookeeper.{CreateMode, KeeperException, ZooDefs, Watcher, WatchedEvent}
import scala.collection.JavaConversions._

// TODO this was stolen straight up from ostrich-aggregator. we should switch to use the scribe library
final class ResilientZKNode(
                             path: String,
                             nodeName: String,
                             zkClient: ZooKeeperClient,
                             timer: Timer,
                             statsReceiver: StatsReceiver = NullStatsReceiver
                             ) {
  self =>

  require(path.startsWith("/"), "zookeeper path must start with '/'")

  private[this] val scopedStatsReceiver = statsReceiver.scope("zookeeper")
  private[this] val registerCounter = scopedStatsReceiver.counter("registers")
  private[this] val expirationCounter = scopedStatsReceiver.counter("expirations")
  private[this] val deletionCounter = scopedStatsReceiver.counter("deletions")
  private[this] val disconnectCounter = scopedStatsReceiver.counter("disconnects")
  private[this] val failureCounter = scopedStatsReceiver.counter("failures")
  private[this] val fullPath = path + "/" + nodeName
  private[this] val logger = Logger.get("ZookeeperLog")
  private[this] val OPEN_ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE
  @volatile private[this] var register = true

  private[this] val watcher = new Watcher {
    override def process(e: WatchedEvent) {
      logger.info("processing event: " + e.toString)

      if (e.getType == Watcher.Event.EventType.NodeDeleted) {
        deletionCounter.incr()
        self.register()
      } else {
        e.getState match {
          case Watcher.Event.KeeperState.Disconnected =>
            disconnectCounter.incr()
            self.register()
          case Watcher.Event.KeeperState.Expired =>
            expirationCounter.incr()
            self.register()
          case _ => ()
        }
      }
    }
  }

  def register() {
    synchronized {
      if (!register) {
        return
      }
      registerCounter.incr()
      logger.info("registering " + fullPath)
      ensureParentExists()
      if (createNode(fullPath, false)) {
        watchForDeletions()
      } else {
        failureCounter.incr()
        scheduleRegister()
      }
    }
  }

  /**
   * Delete and stop recreating the node.
   */
  @throws(classOf[KeeperException])
  def unregister() {
    synchronized {
      register = false
      try {
        zkClient.get().delete(fullPath, -1)
      } catch {
        case e: KeeperException.NoNodeException => () // node doesn't exist, no need to delete it
      }
    }
  }

  private[this] def watchForDeletions() {
    zkClient.get().exists(fullPath, watcher)
  }

  private[this] def ensureParentExists() {
    val parts = path.slice(1, path.size).split("/")
    var currentPath = ""

    parts foreach {
      part =>
        currentPath = currentPath + "/" + part
        createNode(currentPath, true)
    }
  }

  private[this] def createNode(path: String, isPersistent: Boolean): Boolean = {
    try {
      if (zkClient.get().exists(path, false) == null) {
        logger.info("creating node: " + path)

        val mode = if (isPersistent)
          CreateMode.PERSISTENT
        else
          CreateMode.EPHEMERAL

        zkClient.get().create(path, null, OPEN_ACL, mode)
      }
      true
    } catch {
      case e: KeeperException if e.code == KeeperException.Code.NODEEXISTS =>
        logger.info("node already exists")
        true
      case e: Throwable =>
        logger.error("error creating zookeeper node: %s".format(e.toString))
        false
    }
  }

  private[this] def scheduleRegister() {
    timer.schedule(30.seconds.fromNow) {
      register()
    }
  }
}