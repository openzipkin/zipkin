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

package com.twitter.zipkin.zookeeper

import com.google.common.base.Supplier
import com.twitter.app.App
import com.twitter.common.base.ExceptionalCommand
import com.twitter.common.quantity.Amount
import com.twitter.common.quantity.{Time => CommonTime}
import com.twitter.common.zookeeper._
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.twitter.util._
import com.twitter.zk.{Connector, ZkClient, ZNode}
import java.net.InetSocketAddress
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.apache.zookeeper.{CreateMode, KeeperException, Watcher, ZooDefs}
import scala.collection.JavaConverters._
import scala.collection.mutable

trait ZooKeeperClientFactory { self: App =>
  val zkServerLocations = flag("zipkin.zookeeper.location", Seq(new InetSocketAddress(2181)), "Location of the ZooKeeper server")
  val zkServerCredentials = flag("zipkin.zookeeper.credentials", "[none]", "Optional credentials of the form 'username:password'")
  lazy val zkClient = {
    val creds = zkServerCredentials.get map { creds =>
      val Array(u, p) = creds.split(':')
      (u, p)
    }
    new ZKClient(zkServerLocations(), creds)
  }
}

trait ZkWatch[T] extends Closable {
  val data: Var[T]
}

class ZKClient(
  addrs: Seq[InetSocketAddress],
  credentials: Option[(String, String)] = None,
  timeout: Duration = 3.seconds,
  stats: StatsReceiver = DefaultStatsReceiver.scope("ZKClient"),
  log: Logger = Logger.get("ZKClient"),
  timer: Timer = DefaultTimer.twitter,
  pool: FuturePool = FuturePool.unboundedPool
) extends Closable {
  private[this] val client = credentials map { case (u, p) =>
    new ZooKeeperClient(Amount.of(timeout.inMilliseconds.toInt, CommonTime.MILLISECONDS), ZooKeeperClient.digestCredentials(u, p), addrs.asJava)
  } getOrElse {
    new ZooKeeperClient(Amount.of(timeout.inMilliseconds.toInt, CommonTime.MILLISECONDS), addrs.asJava)
  }

  private[this] val zkClient = ZkClient(new Connector {
    client.register(sessionBroker)
    def apply() = pool(client.get(Amount.of(timeout.inMilliseconds, CommonTime.MILLISECONDS)))
    def release() = pool(client.close())
  }).withAcl(ZooDefs.Ids.OPEN_ACL_UNSAFE.asScala)

  private[this] val groups: mutable.Map[String, Group] = new mutable.HashMap[String, Group]

  private[this] def groupFor(path: String): Group = groups.get(path) getOrElse {
    synchronized {
      groups.get(path) getOrElse {
        val group = new Group(client, ZooDefs.Ids.OPEN_ACL_UNSAFE, path)
        groups(path) = group
        group
      }
    }
  }

  private[this] def ensurePath(path: String): Future[Unit] = {
    path.slice(1, path.size).split("/").foldLeft(Future.value("")) { (f, p) =>
      f map { _ + "/" + p } flatMap { path =>
        zkClient(path).exists() rescue {
          // Node doesn't exist yet
          case e: KeeperException.NoNodeException =>
            create(path) rescue {
              // Someone beat us to creation
              case e: KeeperException.NodeExistsException => Future.Done
            }
        } map { _ => path }
      }
    }.unit
  }

  private[this] def create(path: String, persist: Boolean = true, data: Option[Array[Byte]] = None): Future[Unit] = {
    log.info("creating node: " + path)
    val cMode = if (persist) CreateMode.PERSISTENT else CreateMode.EPHEMERAL
    val cData = data getOrElse Array.empty[Byte]
    zkClient(path).create(data = cData, mode = cMode).unit rescue {
      case e: KeeperException.NodeExistsException =>
        log.info("Node exists: " + path)
        Future.Unit
    } onSuccess { _ =>
      log.info("created node: " + path)
    } onFailure { e =>
      log.error(e, "failed to create node: " + path)
    }
  }

  def setData(path: String, data: Array[Byte]): Future[Unit] = {
    zkClient(path).setData(data, -1).rescue {
      case e: KeeperException.NoNodeException =>
        ensurePath(path) before create(path, true, Some(data))
    }.unit
  }

  /**
   * Create a persistent ephemeral node at `path` with `data`. The node will be persisted for as long as
   * the jvm process is alive or until it is closed
   */
  def createEphemeral(path: String, data: Array[Byte] = Array.empty[Byte]): Closable = {
    log.info("creating ephemeral: " + path)
    new Closable {
      private[this] val closed = new AtomicBoolean(false)

      private[this] def register() {
        if (closed.get) return

        log.info("registering ephemeral: " + path)

        ensurePath(path.split("/").init.mkString("/")) before create(path, false, Some(data)) onSuccess { _ =>
          zkClient(path).exists.watch() onSuccess { case ZNode.Watch(_, update) =>
            update onSuccess {
              case e if e.getType == Watcher.Event.EventType.NodeDeleted =>
                log.info("ephemeral node (%s) deleted. re-registering".format(path))
                register()

              case e if e.getState == Watcher.Event.KeeperState.Disconnected =>
                log.info("ephermal node (%s) keeper disconnected. re-registering".format(path))
                register()

              case e if e.getState == Watcher.Event.KeeperState.Expired =>
                log.info("ephemeral node (%s) keeper expired. re-registering".format(path))
                register()

              case e =>
                log.info("ephemeral node (%s) watch fired (ignoring): " + e)
                ()
            }
          }
        } onFailure { e: Throwable =>
          log.error("failed to register ephemeral: " + path, e)
        }
      }
      register()

      def close(deadline: Time): Future[Unit] = {
        if (closed.getAndSet(true)) Future.Unit else {
          zkClient(path).delete().unit
        }
      }
    }
  }

  def watchData(path: String): ZkWatch[Array[Byte]] = {
    log.info("setting watch for: " + path)

    new ZkWatch[Array[Byte]] {
      val data = Var(Array.empty[Byte])

      private[this] val closed = new AtomicBoolean(false)
      private[this] def watch(): Future[Unit] = {
        zkClient(path).getData.watch() flatMap {
          case ZNode.Watch(Return(nodeData), update) if !closed.get =>
            log.info("watch fired with data: " + path)
            data.update(nodeData.bytes)
            update flatMap { _ => watch() }
          case _ =>
            log.info("watch fired (ignoring): " + path)
            Future.Unit
        }
      }
      ensurePath(path) before watch()

      def close(deadline: Time): Future[Unit] = {
        closed.getAndSet(true)
        Future.Unit
      }
    }
  }

  def joinGroup(path: String, data: Var[Array[Byte]]): Closable = {
    log.info("joining group: " + path)
    new Closable {
      private[this] val curVal = new AtomicReference[Array[Byte]](Array.empty[Byte])

      val membership = ensurePath(path) map { _ =>
        groupFor(path).join(new Supplier[Array[Byte]] {
          def get(): Array[Byte] = {
            log.info("updating data for group: " + path)
            curVal.get
          }
        })
      }

      val witness = membership map { m =>
        data.changes.register(Witness { newVal =>
          curVal.set(newVal)
          pool { m.updateMemberData() }
        })
      }

      def close(deadline: Time): Future[Unit] =
        witness flatMap { _.close(deadline) } flatMap { _ =>
          membership flatMap { m => pool { m.cancel() } }
        }
    }
  }

  def groupData(path: String, freq: Duration): ZkWatch[Seq[Array[Byte]]] = {
    log.info("watching group data: " + path)
    new ZkWatch[Seq[Array[Byte]]] {
      val data = Var(Seq.empty[Array[Byte]])

      private[this] val group = ensurePath(path) map { _ => groupFor(path) }

      // ensure this only ever runs once per `freq` thus ensuring only one
      // thread will ever be updating the Var.
      private[this] def update(): Future[Unit] = group flatMap { g =>
        Future.collect(g.getMemberIds.asScala.toSeq map { id =>
          zkClient(g.getMemberPath(id)).getData().map(_.bytes)
        }).onSuccess { newVal =>
          log.info("updating data for group: " + path)
          data.update(newVal)
        }.delayed(freq)(timer) flatMap { _ =>
          update()
        }
      }

      private[this] val updater = update()

      def close(deadline: Time): Future[Unit] = {
        updater.raise(new Exception("stop"))
        Future.Unit
      }
    }
  }

  def offerLeadership(path: String): ZkWatch[Boolean] = {
    log.info("offering leadership: " + path)
    new ZkWatch[Boolean] {
      val data = Var(false)

      private[this] val closed = new AtomicBoolean(false)
      private[this] val abdicate = new AtomicReference[ExceptionalCommand[Group.JoinException]]()

      pool {
        (new CandidateImpl(groupFor(path))).offerLeadership(new Candidate.Leader {
          def onElected(cmd: ExceptionalCommand[Group.JoinException]) {
            if (closed.get) cmd.execute() else {
              log.info("elected as leader: " + path)
              abdicate.set(cmd)
              data.update(true)
            }
          }
          def onDefeated() {
            log.info("defeated in leader election: " + path)
            data.update(false)
          }
        })
      }

      def close(deadline: Time): Future[Unit] = {
        if (closed.getAndSet(true)) Future.Unit else pool {
          val cmd = abdicate.getAndSet(null)
          if (cmd != null) cmd.execute()
          ()
        }
      }
    }
  }

  def close(deadline: Time): Future[Unit] = {
    zkClient.release()
  }
}
