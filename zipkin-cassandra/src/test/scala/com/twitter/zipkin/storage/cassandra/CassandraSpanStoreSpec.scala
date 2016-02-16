package com.twitter.zipkin.storage.cassandra

import com.twitter.zipkin.storage.SpanStoreSpec
import org.junit.{AssumptionViolatedException, BeforeClass}

object CassandraSpanStoreSpec {

  /** This intentionally silently aborts when cassandra is not running on localhost. */
  @BeforeClass def ensureCassandra: Unit = {
    try {
      CassandraFixture.repository
    } catch {
      case e: Exception => throw new AssumptionViolatedException("Cassandra not running", e)
    }
  }
}

class CassandraSpanStoreSpec extends SpanStoreSpec {

  override val store = new CassandraSpanStore {
    /** Deferred as repository creates network connections */
    override lazy val repository = CassandraFixture.repository
  }

  override def clear = CassandraFixture.truncate
}
