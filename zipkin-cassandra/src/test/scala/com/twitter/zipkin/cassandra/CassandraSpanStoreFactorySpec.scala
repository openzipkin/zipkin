package com.twitter.zipkin.cassandra

import com.datastax.driver.core.Cluster
import com.twitter.app.App
import java.net.InetSocketAddress
import java.util.Arrays.asList
import org.scalatest.{FunSuite, Matchers}

class CassandraSpanStoreFactorySpec extends FunSuite with Matchers {
  object TestFactory extends App with CassandraSpanStoreFactory

  test("zipkin.store.cassandra.dest default") {
    TestFactory.nonExitingMain(Array())

    TestFactory.addContactPoint(Cluster.builder()).getContactPoints should be(
      asList(new InetSocketAddress("127.0.0.1", 9042))
    )
  }

  test("zipkin.store.cassandra.dest as host") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1"
    ))

    TestFactory.addContactPoint(Cluster.builder()).getContactPoints should be(
      asList(new InetSocketAddress("1.1.1.1", 9042))
    )
  }

  test("zipkin.store.cassandra.dest as host:port") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9142"
    ))

    TestFactory.addContactPoint(Cluster.builder()).getContactPoints should be(
      asList(new InetSocketAddress("1.1.1.1", 9142))
    )
  }

  test("zipkin.store.cassandra.dest as host1:port,host2 (default port)") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9143,2.2.2.2"
    ))

    TestFactory.addContactPoint(Cluster.builder()).getContactPoints should be(
      asList(new InetSocketAddress("1.1.1.1", 9143), new InetSocketAddress("2.2.2.2", 9042))
    )
  }
}
