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

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.common.net.HostAndPort
import com.twitter.app.App
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.zipkin.storage.cassandra._
import org.twitter.zipkin.storage.cassandra.Repository
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy
import com.twitter.app.Flag

trait CassandraSpanStoreFactory {self: App =>

  import com.twitter.zipkin.storage.cassandra.{CassandraSpanStoreDefaults => Defaults}

  val keyspace              = flag("zipkin.store.cassandra.keyspace", Defaults.KeyspaceName, "name of the keyspace to use")
  val cassandraDest         = flag("zipkin.store.cassandra.dest", "localhost:9042", "dest of the cassandra cluster; comma-separated list of host:port pairs")
  val cassandraSpanTtl      = flag("zipkin.store.cassandra.spanTTL", Defaults.SpanTtl, "length of time cassandra should store spans")
  val cassandraIndexTtl     = flag("zipkin.store.cassandra.indexTTL", Defaults.IndexTtl, "length of time cassandra should store span indexes")
  val cassandraMaxTraceCols = flag("zipkin.store.cassandra.maxTraceCols", Defaults.MaxTraceCols, "max number of spans to return from a query")
  val cassandraUser: Flag[String]     = flag("zipkin.store.cassandra.user", "cassandra authentication user name")
  val cassandraPassword: Flag[String] = flag("zipkin.store.cassandra.password", "cassandra authentication password")


  def newCassandraStore(stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore")): CassandraSpanStore = {
    val repository = new Repository(keyspace(), createClusterBuilder().build())

    new CassandraSpanStore(
      repository,
      stats.scope(keyspace()),
      cassandraSpanTtl(),
      cassandraIndexTtl(),
      cassandraMaxTraceCols())
  }

  def createClusterBuilder(): Cluster.Builder = {
    val builder = addContactPoint(Cluster.builder())
    if(cassandraUser.isDefined && cassandraPassword.isDefined)
      builder.withCredentials(cassandraUser(), cassandraPassword())

    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
      .withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(new RoundRobinPolicy()).build()))
  }

  def addContactPoint(builder: Cluster.Builder): Cluster.Builder = {
    val contactPoints = cassandraDest().split(",").map(HostAndPort.fromString)

    if (contactPoints.length > 1) {
      val addresses = contactPoints.map(cp => new java.net.InetSocketAddress(cp.getHostText, cp.getPortOrDefault(9042)))
      builder.addContactPointsWithPorts(collection.JavaConversions.asJavaCollection(addresses))
    } else {
      val contactPoint = contactPoints.head
      builder.addContactPoint(contactPoint.getHostText)
        .withPort(contactPoint.getPortOrDefault(9042))
    }
  }
}
