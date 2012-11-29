package com.twitter.zipkin.cassandra

import com.twitter.conversions.time._
import com.twitter.cassie.{ServerSetsCluster, Cluster, Keyspace}
import com.twitter.util.{Duration, Config}
import com.twitter.finagle.stats.{StatsReceiver, NullStatsReceiver}
import com.twitter.finagle.tracing.{Tracer, NullTracer}

object KeyspaceBuilder {
  def zookeeperServerSets(
    hosts: Seq[(String, Int)],
    path: String,
    timeout: Duration,
    stats: StatsReceiver = NullStatsReceiver) = {

    val clusterBuilder = new Config[Cluster] {
      def apply() = {
        new ServerSetsCluster(hosts, path, timeout.inMillis.toInt, stats)
      }
    }
    KeyspaceBuilder(clusterBuilder)
  }

  def static(
    nodes: Set[String] = Set("localhost"),
    port: Int = 9160,
    stats: StatsReceiver = NullStatsReceiver,
    tracerFactory: Tracer.Factory = NullTracer.factory) = {

    val clusterBuilder = new Config[Cluster] {
      def apply() = new Cluster(nodes, port, stats, tracerFactory)
    }
    KeyspaceBuilder(clusterBuilder)
  }
}

case class KeyspaceBuilder(
  clusterBuilder: Config[Cluster],
  name: String = "Zipkin",
  connectTimeout: Duration = 10.seconds,
  requestTimeout: Duration = 20.seconds,
  attempts: Int = 3,
  maxConnectionsPerHost: Int = 400,
  hostConnectionMaxWaiters: Int = 5000,
  mapHosts: Boolean = true
) extends Config[Keyspace] {

  def name(n: String)                  = this.copy(name = n)
  def connectTimeout(t: Duration)      = this.copy(connectTimeout = t)
  def requestTimeout(t: Duration)      = this.copy(requestTimeout = t)
  def attempts(a: Int)                 = this.copy(attempts = a)
  def maxConnectionsPerHost(m: Int)    = this.copy(maxConnectionsPerHost = m)
  def hostConnectionMaxWaiters(h: Int) = this.copy(hostConnectionMaxWaiters = h)
  def mapHosts(m: Boolean)             = this.copy(mapHosts = m)

  def apply(): Keyspace = {
    val cluster = clusterBuilder()
    val c = if (mapHosts) {
      cluster
    } else {
      cluster.mapHostsEvery(0.seconds)
    }

    c.keyspace(name)
      .connectTimeout(connectTimeout.inMillis)
      .requestTimeout(requestTimeout.inMillis)
      .retries(attempts)
      .timeout((connectTimeout.inMillis + requestTimeout.inMillis) * attempts)
      .maxConnectionsPerHost(maxConnectionsPerHost)
      .hostConnectionMaxWaiters(hostConnectionMaxWaiters)
      .connect()
  }
}
