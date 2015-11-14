package com.twitter.zipkin.storage.cassandra

import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec
import org.junit.BeforeClass

object CassandraDependencyStoreSpec {

  @BeforeClass def ensureCassandra = CassandraFixture.cassandra
}

class CassandraDependencyStoreSpec extends DependencyStoreSpec {

  override val store = new CassandraDependencyStore {
    /** Deferred as repository creates network connections */
    override lazy val repository = CassandraFixture.repository
  }

  override def processDependencies(spans: List[Span]) = {
    val deps = new Dependencies(spans.head.timestamp.get, spans.last.timestamp.get, Dependencies.toLinks(spans))
    result(store.storeDependencies(deps))
  }

  override def clear = CassandraFixture.truncate
}
