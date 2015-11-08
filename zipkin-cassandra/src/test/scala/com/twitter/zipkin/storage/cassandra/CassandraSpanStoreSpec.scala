package com.twitter.zipkin.storage.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.storage.SpanStoreSpec
import java.util.Collections
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper.startEmbeddedCassandra
import org.junit.{BeforeClass, Ignore}
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraSpanStoreSpec {
  val keyspace = "test_zipkin_spanstore"
  // Defer shared connection to the cluster
  lazy val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(9142).build()

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

  override val store = new CassandraSpanStore {
    override lazy val repository = new Repository(keyspace, cluster)
  }

  override def clear = cluster.connect().execute("DROP KEYSPACE IF EXISTS " + keyspace)

  @Ignore override def getTraces_lookback() = {
    // TODO!
  }

  @Ignore override def getTraces_duration() = {
    // TODO!
  }
}
