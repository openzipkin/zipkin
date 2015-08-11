package com.twitter.zipkin.storage.cassandra

import com.twitter.app.App
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.storage.SpanStoreSpec
import org.junit.{BeforeClass, Ignore, Test}

object CassieSpanStoreSpec {

  // Extending FakeCassandra makes lifecycle hooks start, stop visible.
  object FakeServer extends FakeCassandra

  // Scala cannot generate fields with public visibility, so use a def instead.
  @BeforeClass def beforeAll() = FakeServer.start()
}

class CassieSpanStoreSpec extends SpanStoreSpec {

  import CassieSpanStoreSpec.FakeServer

  // Lazy to prevent initializing before embedded cassandra starts.
  lazy val store = {
    object CassieStore extends App with CassieSpanStoreFactory
    CassieStore.main(Array("-zipkin.store.cassie.dest", "127.0.0.1:" + FakeServer.port.get))
    CassieStore.newCassandraStore()
  }

  override def clear = {
    FakeServer.reset()
  }

  /** Duration is unreliable in Cassie. */
  @Ignore
  @Test override def getTracesDuration() = super.getTracesDuration()

  @Ignore
  @Test override def getTraceIdsByName() = super.getTraceIdsByName()
}
