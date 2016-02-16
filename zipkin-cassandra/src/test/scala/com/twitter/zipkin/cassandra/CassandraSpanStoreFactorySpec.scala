package com.twitter.zipkin.cassandra

import com.datastax.driver.core.policies.{DCAwareRoundRobinPolicy, LatencyAwarePolicy, RoundRobinPolicy, TokenAwarePolicy}
import com.datastax.driver.core.{AuthProvider, Host, HostDistance}
import com.twitter.app.App
import java.net.InetSocketAddress
import java.util.Arrays.asList
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class CassandraSpanStoreFactorySpec extends FunSuite with Matchers with MockitoSugar {
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

  test("connect port when only one contact point") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9142"
    ))
    TestFactory.createClusterBuilder().getConfiguration().getProtocolOptions().getPort() should be(
      9142
    )
  }

  test("connect port when contact points are consistent") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9143,2.2.2.2:9143"
    ))
    TestFactory.createClusterBuilder().getConfiguration().getProtocolOptions().getPort() should be(
      9143
    )
  }

  test("connect port is default when contact points are when mixed") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.dest", "1.1.1.1:9143,2.2.2.2"
    ))
    TestFactory.createClusterBuilder().getConfiguration().getProtocolOptions().getPort() should be(
      9042
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

  test("Default load-balancing policy is round-robin") {
    TestFactory.nonExitingMain(Array())

    val policy = TestFactory.createClusterBuilder()
      .getConfiguration()
      .getPolicies()
      .getLoadBalancingPolicy()
      .asInstanceOf[TokenAwarePolicy].getChildPolicy
      .asInstanceOf[LatencyAwarePolicy].getChildPolicy
      .asInstanceOf[RoundRobinPolicy]

    val foo = mock[Host]
    when(foo.getDatacenter).thenReturn("foo")
    policy.distance(foo) should be(HostDistance.LOCAL)

    val bar = mock[Host]
    when(bar.getDatacenter).thenReturn("bar")
    policy.distance(bar) should be(HostDistance.LOCAL)
  }

  test("zipkin.store.cassandra.localDc ignores non-local datacenters") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.localDc", "foo"
    ))

    val policy = TestFactory.createClusterBuilder()
      .getConfiguration()
      .getPolicies()
      .getLoadBalancingPolicy()
      .asInstanceOf[TokenAwarePolicy].getChildPolicy
      .asInstanceOf[LatencyAwarePolicy].getChildPolicy
      .asInstanceOf[DCAwareRoundRobinPolicy]

    val foo = mock[Host]
    when(foo.getDatacenter).thenReturn("foo")
    policy.distance(foo) should be(HostDistance.LOCAL)

    val bar = mock[Host]
    when(bar.getDatacenter).thenReturn("bar")
    policy.distance(bar) should be(HostDistance.IGNORED)
  }

  test("zipkin.store.cassandra.maxConnections default") {
    TestFactory.nonExitingMain(Array())

    TestFactory.createClusterBuilder()
      .getConfiguration()
      .getPoolingOptions()
      .getMaxConnectionsPerHost(HostDistance.LOCAL) should be (8)
  }

  test("zipkin.store.cassandra.maxConnections override") {
    TestFactory.nonExitingMain(Array(
      "-zipkin.store.cassandra.maxConnections", "16"
    ))

    TestFactory.createClusterBuilder()
      .getConfiguration()
      .getPoolingOptions()
      .getMaxConnectionsPerHost(HostDistance.LOCAL) should be (16)
  }
}
