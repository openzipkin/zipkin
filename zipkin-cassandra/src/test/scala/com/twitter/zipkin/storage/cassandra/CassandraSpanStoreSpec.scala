package com.twitter.zipkin.storage.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.storage.SpanStoreSpec
import java.util.Collections
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper.startEmbeddedCassandra
import org.junit.BeforeClass
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraSpanStoreSpec {
  val keyspace = "test_zipkin"
  // Defer shared connection to the cluster
  lazy val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(9142).build()

  // Avoid conflicts with thrift 0.5
  System.setProperty("cassandra.start_rpc", "false")

  @BeforeClass def cassandra = {
    startEmbeddedCassandra("cu-cassandra.yaml", "build/embeddedCassandra", 10 * 1000)

    new CQLDataLoader(cluster.connect).load(new CQLDataSet() {
      override def isKeyspaceDeletion = true

      override def getKeyspaceName = keyspace

      override def isKeyspaceCreation = true

      override def getCQLStatements = Collections.emptyList()
    })
  }
}

class CassandraSpanStoreSpec extends SpanStoreSpec {

  import CassandraSpanStoreSpec._

  override lazy val store = new CassandraSpanStore(new Repository(keyspace, cluster))

  override def clear = cluster.connect().execute("DROP KEYSPACE " + keyspace)
}
