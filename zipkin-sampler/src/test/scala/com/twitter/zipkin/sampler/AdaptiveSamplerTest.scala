package com.twitter.zipkin.sampler

import com.twitter.app.App
import com.twitter.finagle.httpx.{HttpMuxer, RequestBuilder}
import com.twitter.util.Await.{ready, result}
import org.apache.curator.test.TestingServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class AdaptiveSamplerTest extends FunSuite with Matchers with BeforeAndAfterAll {

  object TestAdaptiveSampler extends App with AdaptiveSampler

  val zookeeper = new TestingServer()

  override def beforeAll() {
    zookeeper.start()
    TestAdaptiveSampler.nonExitingMain(Array(
      "-zipkin.sampler.adaptive.apiPath", "/",
      "-zipkin.zookeeper.location", zookeeper.getConnectString
    ))
    TestAdaptiveSampler.newAdaptiveSamplerFilter()
    TestAdaptiveSampler.configureAdaptiveSamplerHttpApi()
    ready(TestAdaptiveSampler)
  }

  override def afterAll() {
    zookeeper.close()
    ready(TestAdaptiveSampler.close())
  }

  test("set sampleRate with http api") {
    val update = 0.9

    val setRate = RequestBuilder().url("http://localhost/sampleRate?newRate=" + update).buildGet
    result(HttpMuxer(setRate)).contentString should be("Rate changed to: " + update)
  }

  test("set targetStoreRate with http api") {
    val update = 100

    val setRate = RequestBuilder().url("http://localhost/targetStoreRate?newRate=" + update).buildGet
    result(HttpMuxer(setRate)).contentString should be("Rate changed to: " + update)
  }
}
