// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie

import com.twitter.cassie.connection.CCluster
import com.twitter.cassie.connection.{ClusterClientProvider, SocketAddressCluster, RetryPolicy}
import com.twitter.concurrent.Spool
import com.twitter.finagle.builder.{Cluster => FCluster}
import com.twitter.finagle.ServiceFactory
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.tracing.{ Tracer, NullTracer }
import com.twitter.finagle.WriteException
import org.slf4j.LoggerFactory
import com.twitter.util.{ Duration, Future, Promise, Return, Time, JavaTimer }
import java.io.IOException
import java.net.{ InetSocketAddress, SocketAddress }
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import scala.collection.SeqProxy
import scala.util.parsing.json.JSON

/**
 * Given a seed host and port, returns a set of nodes in the cluster.
 *
 * @param keyspace the keyspace to map
 * @param seeds seed node addresses
 * @param port the Thrift port of client nodes
 */
object ClusterRemapper {
  private val log = LoggerFactory.getLogger(this.getClass)
}
private class ClusterRemapper(
  keyspace: String,
  seeds: Seq[InetSocketAddress],
  remapPeriod: Duration,
  port: Int = 9160,
  statsReceiver: StatsReceiver,
  tracerFactory: Tracer.Factory
) extends CCluster[SocketAddress] {
  import ClusterRemapper._

  private[this] var hosts = seeds
  private[this] var changes = new Promise[Spool[FCluster.Change[SocketAddress]]]

  // Timer keeps updating the host list. Variables "hosts" and "changes" together reflect the cluster consistently
  // at any time
  private[cassie] var timer = new JavaTimer(true)
  timer.schedule(Time.now, remapPeriod) {
    fetchHosts(hosts) onSuccess { ring =>
      log.debug("Received: %s", ring)
      val (added, removed) = synchronized {
        val oldSet = hosts.toSet
        hosts = ring.flatMap { h =>
          collectionAsScalaIterable(h.endpoints).map {
            new InetSocketAddress(_, port)
          }
        }.toSeq
        val newSet = hosts.toSet
        (newSet &~ oldSet, oldSet &~ newSet)
      }
      added foreach { host => appendChange(FCluster.Add(host)) }
      removed foreach { host => appendChange(FCluster.Rem(host)) }
    } onFailure { error =>
      log.error("error mapping ring", error)
      statsReceiver.counter("ClusterRemapFailure." + error.getClass().getName()).incr
    }
  }

  private[this] def appendChange(change: FCluster.Change[SocketAddress]) = {
    val newTail = new Promise[Spool[FCluster.Change[SocketAddress]]]
    changes() = Return(change *:: newTail)
    changes = newTail
  }

  def close = timer.stop()

  def snap: (Seq[SocketAddress], Future[Spool[FCluster.Change[SocketAddress]]]) = (hosts, changes)

  private[this] def fetchHosts(hosts: Seq[SocketAddress]) = {
    val ccp = new ClusterClientProvider(
      new SocketAddressCluster(hosts),
      keyspace,
      retries = 5,
      timeout = Duration(5, TimeUnit.SECONDS),
      requestTimeout = Duration(1, TimeUnit.SECONDS),
      connectTimeout = Duration(1, TimeUnit.SECONDS),
      minConnectionsPerHost = 1,
      maxConnectionsPerHost = 1,
      hostConnectionMaxWaiters = 100,
      statsReceiver = statsReceiver,
      tracerFactory = tracerFactory,
      retryPolicy = RetryPolicy.Idempotent
    )
    ccp map {
      log.info("Mapping cluster...")
      _.describe_ring(keyspace)
    } ensure {
      ccp.close()
    }
  }
}
