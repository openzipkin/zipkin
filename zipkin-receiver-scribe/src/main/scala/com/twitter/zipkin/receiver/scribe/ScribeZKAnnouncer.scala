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
package com.twitter.zipkin.receiver.scribe

import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{StatsReceiver, LoadedStatsReceiver}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.twitter.util.Closable
import com.twitter.util.{FuturePool, Future, Time, Timer}
import com.twitter.zk.ZkClient
import java.net.InetSocketAddress
import org.apache.zookeeper.Watcher.Event
import org.apache.zookeeper.ZooDefs.Ids.{OPEN_ACL_UNSAFE => OpenACL}
import org.apache.zookeeper.{CreateMode, KeeperException}
import scala.collection.JavaConverters._

/**
 * Used to announce an `InetSocketAddress` to a specific ZooKeeper path in a format compatible
 * with what scribe aggregators expect.
 */
final class ScribeZKAnnouncer(
  path: String,
  addr: InetSocketAddress,
  zkClient: ZkClient,
  stats: StatsReceiver = LoadedStatsReceiver.scope("ScribeZKAnnouncer")
) extends Closable { self =>
  require(path.startsWith("/"), "zookeeper path must start with '/'")

  private[this] val client = zkClient.withAcl(OpenACL.asScala)
  private[this] val nodeName = "%s:%d".format(addr.getHostName, addr.getPort)
  private[this] val fullPath = path + "/" + nodeName

  private[this] val RegisterCounter = stats.counter("registers")
  private[this] val ExpirationCounter = stats.counter("expirations")
  private[this] val DeletionCounter = stats.counter("deletions")
  private[this] val DisconnectCounter = stats.counter("disconnects")
  private[this] val FailureCounter = stats.counter("failures")
  private[this] val logger = Logger.get("ScribeZK")

  @volatile private[this] var closed = false

  zkClient onSessionEvent {
    case e if (e.eventType == Event.EventType.NodeDeleted) =>
      DeletionCounter.incr()
      self.register()

    case e if (e.state == Event.KeeperState.Disconnected) =>
      DisconnectCounter.incr()
      self.register()

    case e if (e.state == Event.KeeperState.Expired) =>
      ExpirationCounter.incr()
      self.register()
  }

  private[this] def ensurePath(path: String): Future[Unit] = {
    path.slice(1, path.size).split("/").foldLeft(Future.value("")) { (f, p) =>
      f map { _ + "/" + p } flatMap { path =>
        create(path) map { _ => path } rescue {
          case e: KeeperException.NodeExistsException => Future.value(path)
        }
      }
    } flatMap { _ => Future.Unit }
  }

  private[this] def create(path: String, persist: Boolean = true, data: Option[Array[Byte]] = None): Future[Unit] = {
    logger.info("creating node: %s " + path)
    val cMode = if (persist) CreateMode.PERSISTENT else CreateMode.EPHEMERAL
    client(path).create(mode = cMode) flatMap { _ => Future.Unit } rescue {
      case e: KeeperException.NodeExistsException =>
        logger.info("Node exists: " + path)
        Future.Unit
    } onSuccess { _ =>
      logger.info("creating node: %s " + path)
    } onFailure { e =>
      logger.error(e, "failed to create node: %s " + path)
    }
  }

  def register() {
    if (closed) return

    RegisterCounter.incr()
    logger.info("registering " + fullPath)

    ensurePath(path) flatMap { _ =>
      create(fullPath, false, Some(nodeName.getBytes))
    } onSuccess { _ =>
      logger.info("successfully registrered: " + fullPath)
    } onFailure { e =>
      logger.error(e, "failed to register: " + fullPath)
    }
  }
  register()

  def close(deadline: Time): Future[Unit] = {
    closed = true
    zkClient(fullPath).delete() flatMap { _ => Future.Done }
  }
}
