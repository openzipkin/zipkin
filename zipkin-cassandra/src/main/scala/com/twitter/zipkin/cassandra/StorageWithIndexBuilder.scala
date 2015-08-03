package com.twitter.zipkin.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.cassandra.{CassandraSpanStoreDefaults, CassandraSpanStore}
import com.twitter.zipkin.storage.{SpanStoreStorageWithIndexAdapter, Index, Storage}
import org.twitter.zipkin.storage.cassandra.Repository

/** Allows [[CassandraSpanStore]] to be used with legacy [[Builder]]s. */
case class StorageWithIndexBuilder(
  cluster: Cluster,
  keyspace: String = CassandraSpanStoreDefaults.KeyspaceName
) extends Builder[Storage with Index] {self =>
  def apply() = {
    val repository = new Repository(keyspace, cluster)
    new SpanStoreStorageWithIndexAdapter(new CassandraSpanStore(repository))
  }
}
