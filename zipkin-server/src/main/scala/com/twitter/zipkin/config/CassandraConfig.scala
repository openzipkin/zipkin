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
package com.twitter.zipkin.config

import com.twitter.conversions.time._
import com.twitter.util.Duration
import java.net.InetSocketAddress
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.tracing.{NullTracer, Tracer}
import com.twitter.cassie.{Cluster, ServerSetsCluster, Keyspace}
import com.twitter.zipkin.storage.cassandra.{ScroogeThriftCodec, SnappyCodec}
import com.twitter.zipkin.gen

trait CassandraConfig {

  /* Data TTL */
  var tracesTimeToLive: Duration = 3.days

  /* Should we pick up hosts from ZooKeeper */
  var useServerSets: Boolean = true

  var clusterName: String = "cassandra.prod.zipkin"

  def zkPath: String = "/twitter/service/cassandra/%s".format(clusterName)

  var zkHosts: Seq[InetSocketAddress] = Seq(new InetSocketAddress("localhost", 2181))

  var zkTimeoutMillis: Int = 1.minute.inMilliseconds.toInt

  // or (probably in debug mode) we pick it up from the conf
  var nodes: Set[String] = Set("localhost")
  var port: Int = 9160

  lazy val statsReceiver = new OstrichStatsReceiver

  var mapHosts                 : Boolean = true
  var retries                  : Int = 3
  var timeout                  : Int = 50000 // TODO the relationship between this and the timeout below is unclear
  var requestTimeout           : Int = 20000
  var maxConnectionsPerHost    : Int = 400
  var connectTimeout           : Int = 10000
  var hostConnectionMaxWaiters : Int = 5000

  var tracerFactory: Tracer.Factory = NullTracer.factory

  var keyspaceName           : String = "Zipkin"

  // compress the thrift structs before saving in cassandra
  // decompress on the fly
  implicit val spanCodec = new SnappyCodec(new ScroogeThriftCodec[gen.Span](gen.Span))

  lazy val keyspace: Keyspace = {
    val cluster =
      if (useServerSets) {
        new ServerSetsCluster(zkHosts, zkPath, zkTimeoutMillis, statsReceiver)
      } else {
        var cluster = new Cluster(nodes, port, statsReceiver)
        if (!mapHosts) cluster = cluster.mapHostsEvery(0.seconds)
        cluster
      }

    cluster.keyspace(keyspaceName)
      .connectTimeout(connectTimeout)
      .retries(retries)
      .timeout(timeout)
      .requestTimeout(requestTimeout)
      .maxConnectionsPerHost(maxConnectionsPerHost)
      .tracerFactory(tracerFactory)
      .hostConnectionMaxWaiters(hostConnectionMaxWaiters)
      .connect()
  }
}
