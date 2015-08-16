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
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.common.net.HostAndPort
import com.twitter.app.App
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.zipkin.storage.cassandra._
import org.twitter.zipkin.storage.cassandra.Repository
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy

trait CassandraSpanStoreFactory {self: App =>

  import com.twitter.zipkin.storage.cassandra.{CassandraSpanStoreDefaults => Defaults}


  val keyspace = flag("zipkin.store.cassandra.keyspace", Defaults.KeyspaceName, "name of the keyspace to use")
  val cassandraDest = flag("zipkin.store.cassandra.dest", "localhost:9042", "dest of the cassandra cluster")

  val cassieSpanTtl = flag("zipkin.store.cassandra.spanTTL", Defaults.SpanTtl, "length of time cassandra should store spans")
  val cassieIndexTtl = flag("zipkin.store.cassandra.indexTTL", Defaults.IndexTtl, "length of time cassandra should store span indexes")

  val cassieMaxTraceCols = flag("zipkin.store.cassandra.maxTraceCols", Defaults.MaxTraceCols, "max number of spans to return from a query")

  def newCassandraStore(stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore")): CassandraSpanStore = {
    val repository = new Repository(keyspace(), createClusterBuilder().build())

    new CassandraSpanStore(
      repository,
      stats.scope(keyspace()),
      cassieSpanTtl(),
      cassieIndexTtl(),
      cassieMaxTraceCols())
  }

  def createClusterBuilder(): Cluster.Builder = {
    addContactPoint(Cluster.builder())
      .withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
      .withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(new RoundRobinPolicy()).build()))
  }

  def addContactPoint(builder: Cluster.Builder): Cluster.Builder = {
    val contactPoint = HostAndPort.fromString(cassandraDest())

    builder.addContactPoint(contactPoint.getHostText)
      .withPort(contactPoint.getPortOrDefault(9042))
  }
}
