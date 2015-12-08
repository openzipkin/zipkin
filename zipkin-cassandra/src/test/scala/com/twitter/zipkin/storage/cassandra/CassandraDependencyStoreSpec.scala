package com.twitter.zipkin.storage.cassandra

import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec
import org.junit.{AssumptionViolatedException, BeforeClass}

object CassandraDependencyStoreSpec {

  /** This intentionally silently aborts when cassandra is not running on localhost. */
  @BeforeClass def ensureCassandra: Unit = {
    try {
      CassandraFixture.repository
    } catch {
      case e: Exception => throw new AssumptionViolatedException("Cassandra not running", e)
    }
  }
}

class CassandraDependencyStoreSpec extends DependencyStoreSpec {

  override val store = new CassandraDependencyStore {
    /** Deferred as repository creates network connections */
    override lazy val repository = CassandraFixture.repository
  }

  override def processDependencies(spans: List[Span]) = {
    val deps = new Dependencies(spans.head.timestamp.get / 1000, spans.last.timestamp.get / 1000, Dependencies.toLinks(spans))
    result(store.storeDependencies(deps))
  }

  override def clear = CassandraFixture.truncate
}
