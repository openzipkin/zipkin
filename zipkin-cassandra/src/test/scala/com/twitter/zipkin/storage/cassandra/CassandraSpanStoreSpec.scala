package com.twitter.zipkin.storage.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.util.Await.{ready, result}
import com.twitter.util.Duration
import com.twitter.zipkin.storage.SpanStoreSpec
import java.util.Collections
import junit.framework.Test
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.CQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper.startEmbeddedCassandra
import org.junit.BeforeClass
import org.twitter.zipkin.storage.cassandra.Repository

object CassandraSpanStoreSpec {
  val keyspace = "test_zipkin"
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

class CassandraSpanStoreSpec extends SpanStoreSpec {

  import CassandraSpanStoreSpec._

  override lazy val store = new CassandraSpanStore(new Repository(keyspace, cluster))

  override def clear = cluster.connect().execute("DROP KEYSPACE IF EXISTS " + keyspace)

  override def setTimeToLive() {
    ready(store(Seq(span1)))
    ready(store.setTimeToLive(span1.traceId, 1234.seconds))

    for( i <- 1 to 100) {
      // Repository.storeXXX() methods write asynchronously but don't return a Future so we can't reliably test.
      //  just wait and loop instead
      java.lang.Thread.sleep(50)
      if ((result(store.getTimeToLive(span1.traceId)) - 1234.seconds).abs.inMilliseconds <= (i*50) + 100) {
        return
      }
    }
    throw new AssertionError
  }
}
