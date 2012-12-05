package com.twitter.zipkin.cassandra

import com.twitter.cassie.connection.RetryPolicy
import com.twitter.cassie.{Cluster, ServerSetsCluster, KeyspaceBuilder}
import com.twitter.conversions.time._
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.{NullTracer, Tracer}
import com.twitter.util.Duration
import java.net.InetSocketAddress

object Keyspace {
  def zookeeperServerSets(
    keyspaceName: String = "Zipkin",
    hosts: Seq[(String, Int)],
    path: String,
    timeout: Duration,
    stats: StatsReceiver = NullStatsReceiver): KeyspaceBuilder = {

    val sockets = hosts map { case (h, p) => new InetSocketAddress(h, p) }
    useDefaults {
      new ServerSetsCluster(sockets, path, timeout.inMillis.toInt, stats)
        .keyspace(keyspaceName)
    }
  }

  def static(
    keyspaceName: String = "Zipkin",
    nodes: Set[String] = Set("localhost"),
    port: Int = 9160,
    stats: StatsReceiver = NullStatsReceiver,
    tracerFactory: Tracer.Factory = NullTracer.factory): KeyspaceBuilder = {

    useDefaults {
      new Cluster(nodes, port, stats, tracerFactory)
        .keyspace(keyspaceName)
    }
  }

  def useDefaults(keyspaceBuilder: KeyspaceBuilder): KeyspaceBuilder = {
    keyspaceBuilder
      .connectTimeout(10.seconds.inMillis.toInt)
      .requestTimeout(20.seconds.inMillis.toInt)
      .timeout(90.seconds.inMillis.toInt)
      .retries(3)
      .maxConnectionsPerHost(400)
      .hostConnectionMaxWaiters(5000)
      .retryPolicy(RetryPolicy.Idempotent)
  }
}
