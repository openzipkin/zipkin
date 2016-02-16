package com.twitter.zipkin.storage.cassandra

import com.datastax.driver.core.Cluster
import com.google.common.util.concurrent.Futures
import org.twitter.zipkin.storage.cassandra.Repository
import scala.collection.JavaConversions

/** Ensures all cassandra micro-integration tests use only one cassandra server. */
object CassandraFixture {
  val keyspace = "test_zipkin_spanstore"

  // Defer shared connection to the cluster
  lazy val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(9042).build()

  // Ensure the repository's local cache of service names expire quickly
  System.setProperty("zipkin.store.cassandra.internal.writtenNamesTtl", "1")

  // the "true" at the end will ensure schema. lazy to do this only once.
  lazy val repository = new Repository(keyspace, cluster, true)

  def truncate = {
    repository // dereference to ensure schema exists
    val session = cluster.connect()
    Futures.allAsList(JavaConversions.asJavaIterable(Seq(
      "traces",
      "dependencies",
      "service_names",
      "span_names",
      "service_name_index",
      "service_span_name_index",
      "annotations_index",
      "span_duration_index"
    ).map(cf => session.executeAsync("TRUNCATE %s.%s".format(keyspace, cf))))).get()
  }
}
