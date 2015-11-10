package com.twitter.zipkin.storage.cassandra

import com.datastax.driver.core.Cluster
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper._
import org.twitter.zipkin.storage.cassandra.Repository
import java.util.Collections

class CassandraFixture(val keyspace: String) {
  // Defer shared connection to the cluster
  lazy val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(9142).build()

  def cassandra = {
    startEmbeddedCassandra("cu-cassandra.yaml", "build/embeddedCassandra", 10 * 1000)

    new CQLDataLoader(cluster.connect).load(new CQLDataSet() {
      override def isKeyspaceDeletion = false

      override def getKeyspaceName = keyspace

      override def isKeyspaceCreation = false

      override def getCQLStatements = Collections.emptyList()
    })
  }

  def truncate = {
    new Repository(keyspace, cluster, true) // initialize the repository, which creates the keyspace.
    val connection = cluster.connect()
    Seq(
      "traces",
      "dependencies",
      "service_names",
      "span_names",
      "service_name_index",
      "service_span_name_index",
      "annotations_index"
    ).foreach(cf => connection.execute("TRUNCATE %s.%s".format(keyspace, cf)))
  }
}
