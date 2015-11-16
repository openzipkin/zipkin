package com.twitter.zipkin.query

import com.twitter.conversions.time._
import com.twitter.finagle.http.Request
import com.twitter.zipkin.storage.{InMemorySpanStore, NullDependencyStore}
import org.scalatest.{FunSuite, Matchers}

class ZipkinQueryServerTest extends FunSuite with Matchers {

  test("zipkin.queryService.limit override") {
    val query = new ZipkinQueryServer(new InMemorySpanStore, new NullDependencyStore) {
      override def postWarmup() = Unit // don't start a server
      override def waitForServer() = Unit // don't wait for a server
    }
    query.nonExitingMain(Array("-zipkin.queryService.limit", "1000"))
    val queryRequest = query.injector.instance[QueryExtractor].apply(Request("?serviceName=foo")).get

    queryRequest.limit should be(1000)
    queryRequest.lookback should be(7.days.inMillis)  // default
  }

  test("zipkin.queryService.lookback override") {
    val query = new ZipkinQueryServer(new InMemorySpanStore, new NullDependencyStore) {
      override def postWarmup() = Unit // don't start a server
      override def waitForServer() = Unit // don't wait for a server
    }
    query.nonExitingMain(Array("-zipkin.queryService.lookback", 1.day.inMillis.toString))
    val queryRequest = query.injector.instance[QueryExtractor].apply(Request("?serviceName=foo")).get

    queryRequest.limit should be(10) // default
    queryRequest.lookback should be(86400000L)
  }
}
