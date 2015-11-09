package com.twitter.zipkin.storage.cassandra

import java.util.Collections

import com.datastax.driver.core.Cluster
import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper.startEmbeddedCassandra
import org.junit.BeforeClass
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraDependencyStoreSpec {
  val keyspace = "test_zipkin_dependencystore"
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

class CassandraDependencyStoreSpec extends DependencyStoreSpec {

  import CassandraDependencyStoreSpec._

  override val store = new CassandraDependencyStore {
    override lazy val repository = new Repository(keyspace, cluster, true)
  }

  override def processDependencies(spans: List[Span]) = {
    val deps = new Dependencies(spans.head.timestamp.get, spans.last.timestamp.get, Dependencies.toLinks(spans))
    result(store.storeDependencies(deps))
  }

  override def clear = cluster.connect().execute("DROP KEYSPACE IF EXISTS " + keyspace)
}
