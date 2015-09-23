package com.twitter.zipkin.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.DependencyStore
import com.twitter.zipkin.storage.cassandra.{CassandraSpanStoreDefaults, CassandraDependencyStore}
import org.twitter.zipkin.storage.cassandra.Repository

/** Allows [[CassandraDependencyStore]] to be used with legacy [[Builder]]s. */
case class DependencyStoreBuilder(
  cluster: Cluster,
  keyspace: String = CassandraSpanStoreDefaults.KeyspaceName
) extends Builder[DependencyStore] {self =>

  def apply() = new CassandraDependencyStore(new Repository(keyspace, cluster))
}
