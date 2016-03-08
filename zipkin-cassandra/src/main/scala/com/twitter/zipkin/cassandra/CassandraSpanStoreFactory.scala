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

import java.net.InetSocketAddress

import com.datastax.driver.core.{HostDistance, PoolingOptions, Cluster}
import com.datastax.driver.core.policies.{DCAwareRoundRobinPolicy, LatencyAwarePolicy, TokenAwarePolicy}
import com.google.common.net.HostAndPort
import com.twitter.app.App
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.Duration
import com.twitter.zipkin.storage.cassandra._
import com.twitter.zipkin.storage.cassandra.CassandraSpanStoreDefaults._
import org.twitter.zipkin.storage.cassandra.Repository
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy

import scala.collection.JavaConversions
import scala.collection.JavaConverters._

trait CassandraSpanStoreFactory {self: App =>

  val ensureSchema            = flag[Boolean]  ("zipkin.store.cassandra.ensureSchema", false, "ensures schema exists")
  val keyspace                = flag[String]   ("zipkin.store.cassandra.keyspace", KeyspaceName, "name of the keyspace to use")
  val cassandraDest           = flag[String]   ("zipkin.store.cassandra.dest", "localhost:9042", "dest of the cassandra cluster; comma-separated list of host:port pairs")
  val cassandraSpanTtl        = flag[Duration] ("zipkin.store.cassandra.spanTTL", SpanTtl, "length of time cassandra should store spans")
  val cassandraIndexTtl       = flag[Duration] ("zipkin.store.cassandra.indexTTL", IndexTtl, "length of time cassandra should store span indexes")
  val cassandraMaxTraceCols   = flag[Int]      ("zipkin.store.cassandra.maxTraceCols", MaxTraceCols, "max number of spans to return from a query")
  val cassandraUsername       = flag[String]   ("zipkin.store.cassandra.username", "cassandra authentication user name")
  val cassandraPassword       = flag[String]   ("zipkin.store.cassandra.password", "cassandra authentication password")
  val cassandraLocalDc        = flag[String]   ("zipkin.store.cassandra.localDc", "name of the datacenter that will be considered \"local\" for load balancing")
  val cassandraMaxConnections = flag[Int]      ("zipkin.store.cassandra.maxConnections", MaxConnections, "max pooled connections per datacenter-local host")

  // eagerly makes network connections, so lazy
  private[this] lazy val lazyRepository = new Repository(keyspace(), createClusterBuilder().build(), ensureSchema())

  def newCassandraStore(stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore")) = {
    new CassandraSpanStore(stats.scope(keyspace()), cassandraSpanTtl(), cassandraIndexTtl(), cassandraMaxTraceCols()) {
      override lazy val repository = lazyRepository
    }
  }

  def newCassandraDependencies(stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraDependencyStore")) = {
    new CassandraDependencyStore() {
      override lazy val repository = lazyRepository
    }
  }

  def createClusterBuilder(): Cluster.Builder = {
    val builder = Cluster.builder()
    val contactPoints = parseContactPoints()
    val defaultPort = findConnectPort(contactPoints)
    builder.addContactPointsWithPorts(contactPoints)
    builder.withPort(defaultPort) // This ends up config.protocolOptions.port
    if (cassandraUsername.isDefined && cassandraPassword.isDefined)
      builder.withCredentials(cassandraUsername(), cassandraPassword())
    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
    builder.withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(
      if (cassandraLocalDc.isDefined)
        DCAwareRoundRobinPolicy.builder().withLocalDc(cassandraLocalDc()).build()
      else
        DCAwareRoundRobinPolicy.builder().build()
    ).build()))
    builder.withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(
      HostDistance.LOCAL, cassandraMaxConnections()
    ))
  }

  def parseContactPoints() = {
    JavaConversions.seqAsJavaList(cassandraDest().split(",")
      .map(HostAndPort.fromString)
      .map(cp => new java.net.InetSocketAddress(cp.getHostText, cp.getPortOrDefault(9042))))
  }

  /** Returns the consistent port across all contact points or 9042 */
  def findConnectPort(contactPoints: java.util.List[InetSocketAddress]) = {
    val ports = contactPoints.asScala.map(_.getPort).toSet
    if (ports.size == 1) {
      ports.head
    } else {
      9042
    }
  }
}
