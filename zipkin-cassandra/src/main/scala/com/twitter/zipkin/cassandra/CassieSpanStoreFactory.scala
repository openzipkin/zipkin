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
package com.twitter.zipkin.cassandra

import com.twitter.app.{App, Flaggable}
import com.twitter.cassie._
import com.twitter.cassie.connection.{CCluster, RetryPolicy}
import com.twitter.concurrent.Spool
import com.twitter.concurrent.Spool.*::
import com.twitter.conversions.time._
import com.twitter.finagle.{Addr, Name, Resolver}
import com.twitter.finagle.builder.Cluster
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.DefaultTracer
import com.twitter.finagle.util.InetSocketAddressUtil
import com.twitter.util.{Await, Future, Promise, Return, Var}
import com.twitter.zipkin.gen.{Span => ThriftSpan}
import com.twitter.zipkin.storage.cassandra._
import java.net.SocketAddress
import scala.collection.mutable.HashSet

trait CassieSpanStoreFactory { self: App =>
  import com.twitter.zipkin.storage.cassandra.{CassieSpanStoreDefaults => Defaults}

  implicit object flagOfWriteConsistency extends Flaggable[WriteConsistency] {
    def parse(v: String) = v.toLowerCase match {
      case "one" => WriteConsistency.One
      case "any" => WriteConsistency.Any
      case "quorum" => WriteConsistency.Quorum
      case "localquorum" => WriteConsistency.LocalQuorum
      case "eachquorum" => WriteConsistency.EachQuorum
      case "all" => WriteConsistency.All
    }

    override def show(wc: WriteConsistency) =
      wc.toString.split('.')(1)
  }

  implicit object flagOfReadConsistency extends Flaggable[ReadConsistency] {
    def parse(v: String) = v.toLowerCase match {
      case "one" => ReadConsistency.One
      case "quorum" => ReadConsistency.Quorum
      case "localquorum" => ReadConsistency.LocalQuorum
      case "eachquorum" => ReadConsistency.EachQuorum
      case "all" => ReadConsistency.All
    }

    override def show(wc: ReadConsistency) =
      wc.toString.split('.')(1)
  }

  /**
   * Gross
   *
   * Cassie requires a `com.twitter.finagle.builder.Cluster`. The new API is Var[Addr] produced from
   * a Resolver. This bridges the old and new.
   */
  private class VarAddrCluster(va: Var[Addr]) extends CCluster[SocketAddress] {
    private[this] val underlyingSet = new HashSet[SocketAddress]
    private[this] var changes = new Promise[Spool[Cluster.Change[SocketAddress]]]

    private[this] def appendUpdate(update: Cluster.Change[SocketAddress]) = {
      val newTail = new Promise[Spool[Cluster.Change[SocketAddress]]]
      changes() = Return(update *:: newTail)
      changes = newTail
    }

    private[this] def performChange(newSet: Set[SocketAddress]) = synchronized {
      val added = newSet &~ underlyingSet
      val removed = underlyingSet &~ newSet
      added foreach { address =>
        underlyingSet += address
        appendUpdate(Cluster.Add(address))
      }
      removed foreach { address =>
        underlyingSet -= address
        appendUpdate(Cluster.Rem(address))
      }
    }

    private[this] val observer = va observe {
      case Addr.Bound(sockaddrs) => performChange(sockaddrs)
      case _ => ()
    }

    def snap: (Seq[SocketAddress], Future[Spool[Cluster.Change[SocketAddress]]]) = synchronized {
      (underlyingSet.toSeq, changes)
    }

    def close { Await.ready(observer.close()) }
  }

  val cassieColumnFamilies = Defaults.ColumnFamilyNames
  val cassieSpanCodec = Defaults.SpanCodec

  val cassieKeyspace = flag("zipkin.store.cassie.keyspace", Defaults.KeyspaceName, "name of the keyspace to use")
  val cassieDest = flag("zipkin.store.cassie.dest", "localhost:9160", "dest of the cassandra cluster")

  val cassieWriteConsistency = flag[WriteConsistency]("zipkin.store.cassie.writeConsistency", Defaults.WriteConsistency,  "cassie write consistency (one, quorum, all)")
  val cassieReadConsistency = flag[ReadConsistency]("zipkin.store.cassie.readConsistency", Defaults.ReadConsistency, "cassie read consistency (one, quorum, all)")

  val cassieSpanTtl = flag("zipkin.store.cassie.spanTTL", Defaults.SpanTtl, "length of time cassandra should store spans")
  val cassieIndexTtl = flag("zipkin.store.cassie.indexTTL", Defaults.IndexTtl, "length of time cassandra should store span indexes")
  val cassieIndexBuckets = flag("zipkin.store.cassie.indexBuckets", Defaults.IndexBuckets, "number of buckets to split index data into")

  val cassieMaxTraceCols = flag("zipkin.store.cassie.maxTraceCols", Defaults.MaxTraceCols, "max number of spans to return from a query")
  val cassieReadBatchSize = flag("zipkin.store.cassie.readBatchSize", Defaults.ReadBatchSize, "max number of rows per query")

  def newCassandraStore(stats: StatsReceiver = DefaultStatsReceiver.scope("cassie")): CassieSpanStore = {
    val scopedStats = stats.scope(cassieKeyspace())
    val Name.Bound(addr) = Resolver.eval(cassieDest())
    val cluster = new VarAddrCluster(addr)
    //TODO: properly tune these
    val keyspace = KeyspaceBuilder(cluster, cassieKeyspace(), scopedStats, { () => DefaultTracer })

    new CassieSpanStore(
      keyspace.connect(),
      scopedStats,
      cassieColumnFamilies,
      cassieWriteConsistency(),
      cassieReadConsistency(),
      cassieSpanTtl(),
      cassieIndexTtl(),
      cassieIndexBuckets(),
      cassieMaxTraceCols(),
      cassieReadBatchSize(),
      cassieSpanCodec)
  }
}
