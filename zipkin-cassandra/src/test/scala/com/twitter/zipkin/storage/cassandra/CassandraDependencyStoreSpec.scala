package com.twitter.zipkin.storage.cassandra

import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec
import com.twitter.zipkin.storage.anormdb.{AnormSpanStore, AnormDependencyStore, DBConfig, DB}
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

  /**
   * The current implementation does not include dependency aggregation. However, it does include
   * storage and retrieval of pre-aggregated links, and this needs to be tested.
   *
   * <p>This uses anorm to to prepare links, particularly as it is the only tested dependency
   * aggregator in this repository.
   */
  override def processDependencies(spans: List[Span]) = {
    val db = DB(new DBConfig("sqlite-memory", install = true))
    val conn = db.install()
    result(new AnormSpanStore(db, Some(conn)).apply(spans))
    val links = result(new AnormDependencyStore(db, Some(conn)).getDependencies(today + day))

    val deps = new Dependencies(spans.head.timestamp.get / 1000, spans.last.timestamp.get / 1000, links)
    result(store.storeDependencies(deps))
  }

  override def clear = CassandraFixture.truncate
}
