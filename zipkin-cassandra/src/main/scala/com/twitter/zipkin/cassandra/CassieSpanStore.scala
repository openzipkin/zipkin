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
import com.twitter.cassie.connection.RetryPolicy
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{LoadedStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.InetSocketAddressUtil
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.gen.{Span => ThriftSpan}
import com.twitter.zipkin.storage.cassandra._

case class ColumnFamilyNames(
  traces: String = "Traces",
  serviceNames: String = "ServiceNames",
  spanNames: String = "SpanNames",
  serviceNameIndex: String = "ServiceNameIndex",
  serviceSpanNameIndex: String = "ServiceSpanNameIndex",
  annotationsIndex: String = "AnnotationsIndex",
  durationIndex: String = "DurationIndex")

class CassieCluster(val flagVal: String) extends ClusterBase {
  val underlying = flagVal.split("!") match {
    case Array("inet", port, hosts) =>
      new Cluster(hosts, port.toInt)
    case Array("zk", hosts, path) =>
      new ServerSetsCluster(
        InetSocketAddressUtil.parseHosts(hosts).toIterable,
        path,
        90.seconds.inMilliseconds.toInt,
        LoadedStatsReceiver)
    case _ =>
      throw new IllegalArgumentException
  }

  def keyspace(name: String): KeyspaceBuilder =
    underlying.keyspace(name)
}

trait CassieSpanStore { self: App =>
  implicit object flagOfCassieCluster extends Flaggable[CassieCluster] {
    def parse(v: String) = new CassieCluster(v)
    override def show(c: CassieCluster) = c.flagVal
  }

  implicit object flagOfWriteConsistency extends Flaggable[WriteConsistency] {
    def parse(v: String) = v match {
      case "One" => WriteConsistency.One
      case "Any" => WriteConsistency.Any
      case "Quorum" => WriteConsistency.Quorum
      case "LocalQuorum" => WriteConsistency.LocalQuorum
      case "EachQuorum" => WriteConsistency.EachQuorum
      case "All" => WriteConsistency.All
    }

    override def show(wc: WriteConsistency) =
      wc.toString.split(".")(1)
  }

  implicit object flagOfReadConsistency extends Flaggable[ReadConsistency] {
    def parse(v: String) = v match {
      case "One" => ReadConsistency.One
      case "Quorum" => ReadConsistency.Quorum
      case "LocalQuorum" => ReadConsistency.LocalQuorum
      case "EachQuorum" => ReadConsistency.EachQuorum
      case "All" => ReadConsistency.All
    }

    override def show(wc: ReadConsistency) =
      wc.toString.split(".")(1)
  }

  val cassieColumnFamilies = ColumnFamilyNames()
  val cassieSpanCodec = new SnappyCodec(new ScroogeThriftCodec[ThriftSpan](ThriftSpan))

  val cassieKeyspace = flag("zipkin.store.cassie.keyspace", "Zipkin", "name of the keyspace to use")
  val cassieCluster = flag[CassieCluster]("zipkin.store.cassie.cluster", new CassieCluster("inet!9160!localhost"), "location of the cassandra cluster")

  val cassieWriteConsistency = flag[WriteConsistency]("zipkin.store.cassie.writeConsistency", WriteConsistency.One, "cassie write consistency (one, quorum, all)")
  val cassieReadConsistency = flag[ReadConsistency]("zipkin.store.cassie.readConsistency", ReadConsistency.One, "cassie read consistency (one, quorum, all)")

  val cassieSpanTtl = flag("zipkin.store.cassie.spanTTL", 7.days, "length of time cassandra should store spans")
  val cassieIndexTtl = flag("zipkin.store.cassie.indexTTL", 3.days, "length of time cassandra should store span indexes")
  val cassieIndexBuckets = flag("zipkin.store.cassie.indexBuckets", 10, "number of buckets to split index data into")

  val cassieMaxTraceCols = flag("zipkin.store.cassie.maxTraceCols", 100000, "max number of spans to return from a query")
  val cassieReadBatchSize = flag("zipkin.store.cassie.readBatchSize", 500, "max number of rows per query")

  def newCassandraStore(stats: StatsReceiver = LoadedStatsReceiver): CassandraSpanStore = {
    val scopedStats = stats.scope("cassie").scope(cassieKeyspace())
    //TODO: fix these
    val keyspace = cassieCluster().keyspace(cassieKeyspace())
      .connectTimeout(10.seconds.inMillis.toInt)
      .requestTimeout(20.seconds.inMillis.toInt)
      .timeout(90.seconds.inMillis.toInt)
      .retries(3)
      .maxConnectionsPerHost(400)
      .hostConnectionMaxWaiters(5000)
      .retryPolicy(RetryPolicy.Idempotent)
      .reportStatsTo(scopedStats)

    new CassandraSpanStore(
      scopedStats,
      keyspace.connect(),
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
