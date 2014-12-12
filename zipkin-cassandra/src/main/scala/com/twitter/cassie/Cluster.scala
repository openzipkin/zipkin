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

import com.twitter.cassie.connection.{ CCluster, ClusterClientProvider, RetryPolicy, SocketAddressCluster }
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{ StatsReceiver, NullStatsReceiver }
import com.twitter.finagle.tracing.{ Tracer, NullTracer }
import com.twitter.util.Duration
import org.slf4j.LoggerFactory
import java.net.{ SocketAddress, InetSocketAddress }
import scala.collection.JavaConversions._

/**
 * A Cassandra cluster.
 *
 * @param seedHosts list of some hosts in the cluster
 * @param seedPort the port number for '''all''' hosts in the cluster
 *        to refresh its host list.
 * @param stats a finagle stats receiver
 */
class Cluster(seedHosts: Set[String], seedPort: Int, stats: StatsReceiver, tracer: Tracer.Factory) extends ClusterBase {
  private var mapHostsEvery: Duration = 10.minutes

  /**
   * @param seedHosts A comma separated list of seed hosts for a cluster. The rest of the
   *                  hosts can be found via mapping the cluser. See KeyspaceBuilder.mapHostsEvery.
   *                  The port number is assumed to be 9160.
   */
  def this(seedHosts: String, stats: StatsReceiver = NullStatsReceiver) =
    this(seedHosts.split(',').filter { !_.isEmpty }.toSet, 9160, stats, NullTracer.factory)

  /**
   * @param seedHosts A comma separated list of seed hosts for a cluster. The rest of the
   *                  hosts can be found via mapping the cluser. See KeyspaceBuilder.mapHostsEvery.
   */
  def this(seedHosts: String, port: Int) =
    this(seedHosts.split(',').filter { !_.isEmpty }.toSet, port, NullStatsReceiver, NullTracer.factory)

  /**
   * @param seedHosts A collection of seed host addresses. The port number is assumed to be 9160
   */
  def this(seedHosts: java.util.Collection[String]) =
    this(collectionAsScalaIterable(seedHosts).toSet, 9160, NullStatsReceiver, NullTracer.factory)

  /**
   * Returns a  [[com.twitter.cassie.KeyspaceBuilder]] instance.
   * @param name the keyspace's name
   */
  def keyspace(name: String): KeyspaceBuilder = {
    val scopedStats = stats.scope("cassie").scope(name)
    val seedAddresses = seedHosts.map { host => new InetSocketAddress(host, seedPort) }.toSeq
    val cluster = if (mapHostsEvery > 0.seconds)
      // either map the cluster for this keyspace
      new ClusterRemapper(name, seedAddresses, mapHostsEvery, seedPort, stats.scope("remapper"), tracer)
    else
      // or connect directly to the hosts that were given as seeds
      new SocketAddressCluster(seedAddresses)

    KeyspaceBuilder(cluster, name, scopedStats, tracer)
  }

  /**
   * @param d Cassie will query the cassandra cluster every [[period]] period
   *          to refresh its host list.
   */
  def mapHostsEvery(period: Duration): Cluster = {
    mapHostsEvery = period
    this
  }
}

trait ClusterBase {
  /**
   * Returns a  [[com.twitter.cassie.KeyspaceBuilder]] instance.
   * @param name the keyspace's name
   */
  def keyspace(name: String): KeyspaceBuilder
}

object KeyspaceBuilder {
  private val log = LoggerFactory.getLogger(this.getClass)
}


case class KeyspaceBuilder(
  cluster: CCluster[SocketAddress],
  name: String,
  stats: StatsReceiver,
  tracer: Tracer.Factory,
  _retries: Int = 0,
  _timeout: Int = 5000,
  _requestTimeout: Int = 1000,
  _connectTimeout: Int = 1000,
  _minConnectionsPerHost: Int = 1,
  _maxConnectionsPerHost: Int = 5,
  _hostConnectionMaxWaiters: Int = 100,
  _retryPolicy: RetryPolicy = RetryPolicy.Idempotent,
  _failFast: Boolean = false
) {

  import KeyspaceBuilder._

  /**
   * connect to the cluster with the specified parameters
   */
  def connect(): Keyspace = {
    // TODO: move to builder pattern as well
    if (_timeout < _requestTimeout)
      log.error("Timeout (for all requests including retries) is less than the per-request timeout.")

    if (_timeout < _connectTimeout)
      log.error("Timeout (for all requests including retries) is less than the connection timeout.")

    val ccp = new ClusterClientProvider(
      cluster,
      name,
      _retries,
      _timeout.milliseconds,
      _requestTimeout.milliseconds,
      _connectTimeout.milliseconds,
      _minConnectionsPerHost,
      _maxConnectionsPerHost,
      _hostConnectionMaxWaiters,
      stats,
      tracer,
      _retryPolicy,
      _failFast)
    new Keyspace(name, ccp, stats)
  }

  /**
   * In general, it is recommended that you set this to true.
   * It is likely to become the default behavior in Finagle in the not too distant future.
   */
  def failFast(ff: Boolean): KeyspaceBuilder = copy(_failFast = ff)

  def timeout(t: Int): KeyspaceBuilder = copy(_timeout = t)
  def retries(r: Int): KeyspaceBuilder = copy(_retries = r)
  def retryPolicy(r: RetryPolicy): KeyspaceBuilder = copy(_retryPolicy = r)

  /**
   * @see requestTimeout in [[http://twitter.github.com/finagle/finagle-core/target/doc/main/api/com/twitter/finagle/builder/ClientBuilder.html]]
   */
  def requestTimeout(r: Int): KeyspaceBuilder = copy(_requestTimeout = r)

  /**
   * @see connectionTimeout in [[http://twitter.github.com/finagle/finagle-core/target/doc/main/api/com/twitter/finagle/builder/ClientBuilder.html]]
   */
  def connectTimeout(r: Int): KeyspaceBuilder = copy(_connectTimeout = r)

  def minConnectionsPerHost(m: Int): KeyspaceBuilder =
    copy(_minConnectionsPerHost = m)
  def maxConnectionsPerHost(m: Int): KeyspaceBuilder =
    copy(_maxConnectionsPerHost = m)

  /** A finagle stats receiver for reporting. */
  def reportStatsTo(r: StatsReceiver): KeyspaceBuilder = copy(stats = r)

  /** Set a tracer to collect request traces. */
  def tracerFactory(t: Tracer.Factory): KeyspaceBuilder = copy(tracer = t)

  def hostConnectionMaxWaiters(i: Int): KeyspaceBuilder = copy(_hostConnectionMaxWaiters = i)
}
