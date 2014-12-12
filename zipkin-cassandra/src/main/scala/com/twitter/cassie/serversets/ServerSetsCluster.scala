// Copyright 2012 Twitter, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie

import com.google.common.collect.ImmutableSet
import com.twitter.cassie.connection.CCluster
import com.twitter.common.net.pool.DynamicHostSet._
import com.twitter.common.quantity.{Amount, Time}
import com.twitter.common.zookeeper.{ServerSet, ServerSetImpl, ZooKeeperClient}
import com.twitter.finagle.stats.{ StatsReceiver, NullStatsReceiver }
import com.twitter.finagle.tracing.{ Tracer, NullTracer }
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.thrift.ServiceInstance
import java.net.{SocketAddress, InetSocketAddress}
import scala.collection.JavaConversions

class ZookeeperServerSetCCluster(serverSet: ServerSet)
  extends ZookeeperServerSetCluster(serverSet) with CCluster[SocketAddress] {
  def close {}
}

/**
 * A Cassandra cluster where nodes are discovered using ServerSets.
 *
 *  import com.twitter.conversions.time._
 *  val clusterName = "cluster"
 *  val keyspace = "KeyspaceName"
 *  val zkPath = "/twitter/service/cassandra/%s".format(clusterName)
 *  val zkHosts = Seq(new InetSocketAddress("zookeeper.example.com", 2181))
 *  val timeoutMillis = 1.minute.inMilliseconds.toInt
 *  val stats = NullStatsReceiver // or OstrichStatsReciever or whatever
 *
 *  val cluster = new ServerSetsCluster(zkHosts, zkPath, timeoutMillis, stats)
 *  val keyspace = cluster.keyspace(keyspace).connect()
 *
 * @param serverSet zookeeper ServerSet
 * @param stats a finagle stats receiver
 */
class ServerSetsCluster(serverSet: ServerSet, stats: StatsReceiver, tracer: Tracer.Factory) extends ClusterBase {

  private class NoOpMonitor extends HostChangeMonitor[ServiceInstance]  {
    override def onChange(hostSet: ImmutableSet[ServiceInstance]) = {}
  }

  /**
   * Constructor that takes an existing ZooKeeperClient and explicit zk path to a list of servers
   *
   * @param zkClient existing ZooKeeperClient
   * @param zkPath path to node where Cassandra hosts will exist under
   * @param stats a finagle stats receiver
   */
  def this(zkClient: ZooKeeperClient, zkPath: String, stats: StatsReceiver, tracer: Tracer.Factory) =
    this(new ServerSetImpl(zkClient, zkPath), stats, tracer)

  def this(zkClient: ZooKeeperClient, zkPath: String, stats: StatsReceiver) =
    this(zkClient, zkPath, stats, NullTracer.factory)

  /**
   * Convenience constructor that creates a ZooKeeperClient using the specified hosts and timeout.
   *
   * @param zkAddresses list of some ZooKeeper hosts
   * @param zkPath path to node where Cassandra hosts will exist under
   * @param timeoutMillis timeout for ZooKeeper connection
   * @param stats a finagle stats receiver
   */
  def this(zkAddresses: Iterable[InetSocketAddress], zkPath: String, timeoutMillis: Int,
    stats: StatsReceiver = NullStatsReceiver) =
    this(new ZooKeeperClient(Amount.of(timeoutMillis, Time.MILLISECONDS),
      JavaConversions.asJavaIterable(zkAddresses)), zkPath, stats, NullTracer.factory)

  /**
   * Returns a  [[com.twitter.cassie.KeyspaceBuilder]] instance.
   * @param name the keyspace's name
   */
  def keyspace(name: String): KeyspaceBuilder = {
    serverSet.monitor(new NoOpMonitor()) // will block until serverset ready
    val cluster = new ZookeeperServerSetCCluster(serverSet)
    KeyspaceBuilder(cluster, name, stats.scope("cassie").scope(name), tracer)
  }
}
