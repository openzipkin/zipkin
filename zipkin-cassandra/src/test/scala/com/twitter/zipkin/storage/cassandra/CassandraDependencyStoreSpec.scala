package com.twitter.zipkin.storage.cassandra

import java.util.Collections

import com.datastax.driver.core.Cluster
import com.twitter.zipkin.storage.DependencyStoreSpec
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.junit.BeforeClass
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraDependencyStoreSpec {
  val keyspace = "test_zipkin_dependencystore"
  // Defer shared connection to the cluster
  lazy val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(9042).build()

  /**
   * Skips all tests when the default cluster isn't present.
   * @see [[com.datastax.driver.core.exceptions.NoHostAvailableException]]
   */
  @BeforeClass def cassandra = {
    new CQLDataLoader(cluster.connect).load(new CQLDataSet() {
      override def isKeyspaceDeletion = true

      override def getKeyspaceName = keyspace

      override def isKeyspaceCreation = true

      override def getCQLStatements = Collections.emptyList()
    })
  }
}

class CassandraDependencyStoreSpec extends DependencyStoreSpec {

  import CassandraDependencyStoreSpec._

  override lazy val store = new CassandraDependencyStore(new Repository(keyspace, cluster))

  override def clear = cluster.connect().execute("DROP KEYSPACE IF EXISTS " + keyspace)
}
