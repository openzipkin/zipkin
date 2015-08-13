package com.twitter.zipkin.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.SpanStore
import com.twitter.zipkin.storage.cassandra.{CassandraSpanStore, CassandraSpanStoreDefaults}
import org.twitter.zipkin.storage.cassandra.Repository

/** Allows [[CassandraSpanStore]] to be used with legacy [[Builder]]s. */
case class SpanStoreBuilder(
  cluster: Cluster,
  keyspace: String = CassandraSpanStoreDefaults.KeyspaceName
) extends Builder[SpanStore] {self =>

  def apply() = new CassandraSpanStore(new Repository(keyspace, cluster))
}
