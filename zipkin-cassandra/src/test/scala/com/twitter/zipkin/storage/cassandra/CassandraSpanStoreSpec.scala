package com.twitter.zipkin.storage.cassandra

import com.twitter.zipkin.storage.SpanStoreSpec
import org.junit.{BeforeClass, Ignore}
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraSpanStoreSpec extends CassandraFixture("test_zipkin_spanstore") {

  @BeforeClass override def cassandra = super.cassandra
}

class CassandraSpanStoreSpec extends SpanStoreSpec {

  import CassandraSpanStoreSpec._

  override val store = new CassandraSpanStore {
    override lazy val repository = new Repository(keyspace, cluster, true)
  }

  override def clear = truncate

  @Ignore override def getTraces_lookback() = {
    // TODO!
  }

  @Ignore override def getTraces_duration() = {
    // TODO!
  }
}
