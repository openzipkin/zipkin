package com.twitter.zipkin.cassandra

import com.datastax.driver.core.AuthProvider
import com.twitter.app.App
import java.net.InetSocketAddress
import java.util.Arrays.asList
import org.scalatest.{FunSuite, Matchers}

class CassandraSpanStoreFactorySpec extends FunSuite with Matchers {
  object TestFactory extends App with CassandraSpanStoreFactory

  test("zipkin.store.cassandra.dest default") {
    TestFactory.nonExitingMain(Array())

    TestFactory.parseContactPoints() should be(
      asList(new InetSocketAddress("127.0.0.1", 9042))
    )
  }

  test("zipkin.store.cassandra.dest as host") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1"
    ))

    TestFactory.parseContactPoints() should be(
      asList(new InetSocketAddress("1.1.1.1", 9042))
    )
  }

  test("zipkin.store.cassandra.dest as host:port") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9142"
    ))

    TestFactory.parseContactPoints() should be(
      asList(new InetSocketAddress("1.1.1.1", 9142))
    )
  }

  test("zipkin.store.cassandra.dest as host1:port,host2 (default port)") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9143,2.2.2.2"
    ))

    TestFactory.parseContactPoints() should be(
      asList(new InetSocketAddress("1.1.1.1", 9143), new InetSocketAddress("2.2.2.2", 9042))
    )
  }

  test("creatingClusterBuilder with SASL authentication null delimited utf8 bytes") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.username", "bob",
      "-zipkin.store.cassandra.password", "secret"
    ))
    val SASLhandshake = Array[Byte](0, 'b', 'o', 'b', 0, 's', 'e', 'c', 'r', 'e', 't')
    val authProvider = TestFactory.createClusterBuilder()
                         .getConfiguration()
                         .getProtocolOptions()
                         .getAuthProvider()
                         .newAuthenticator(new InetSocketAddress("localhost", 8080))
                         .initialResponse()
    authProvider should be(SASLhandshake)
  }

  test("creatingClusterBuilder without authentication") {
    TestFactory.nonExitingMain(Array())

    TestFactory.createClusterBuilder()
      .getConfiguration()
      .getProtocolOptions()
      .getAuthProvider() should be (AuthProvider.NONE)
  }
}
