package com.twitter.zipkin.storage.cassandra

import com.twitter.zipkin.storage.SpanStoreSpec
import org.junit.BeforeClass

object CassandraSpanStoreSpec {

  @BeforeClass def ensureCassandra = CassandraFixture.cassandra
}

class CassandraSpanStoreSpec extends SpanStoreSpec {

  override val store = new CassandraSpanStore {
    /** Deferred as repository creates network connections */
    override lazy val repository = CassandraFixture.repository
  }

  override def clear = CassandraFixture.truncate
}
