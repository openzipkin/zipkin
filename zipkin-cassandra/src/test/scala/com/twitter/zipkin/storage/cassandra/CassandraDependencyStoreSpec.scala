package com.twitter.zipkin.storage.cassandra

import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec
import org.junit.BeforeClass
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraDependencyStoreSpec extends CassandraFixture("test_zipkin_dependencystore") {

  @BeforeClass override def cassandra = super.cassandra
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

  override def clear = truncate
}
