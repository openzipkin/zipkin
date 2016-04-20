package com.twitter.zipkin.sampler

import java.util.Random

import com.twitter.app.App
import com.twitter.finagle.Service
import com.twitter.util.Await.{ready, result}
import com.twitter.util.Future
import com.twitter.util.Time.now
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import com.twitter.zipkin.storage.InMemorySpanStore
import com.twitter.zipkin.storage.SpanStore.toScalaFunc
import org.apache.curator.framework.CuratorFrameworkFactory.newClient
import org.apache.curator.retry.RetryOneTime
import org.apache.curator.test.TestingServer
import org.junit._
import org.scalactic.Tolerance
import org.scalatest.junit.JUnitSuite

class AdaptiveSamplerTest extends JUnitSuite with Tolerance {

  import AdaptiveSamplerTest._

  @Test def sampleRateReadFromZookeeper() {
    val spanStore = new InMemorySpanStore

    // Simulates an existing sample rate, set from zookeeper
    client.setData().forPath("/sampleRate", Array[Byte]('0','.','9'))

    result(sampler.apply(hundredSpans, Service.mk(spanStore)))

    assert(spanStore.spans.size === (90 +- 10)) // TODO: see if there's a way to tighten this up!
  }

  @Test def ignoresBadRateReadFromZookeeper() {
    val spanStore = new InMemorySpanStore

    // Simulates a bad rate, set from zookeeper
    client.setData().forPath("/sampleRate", Array[Byte]('1','.','9'))

    result(sampler.apply(hundredSpans, Service.mk(spanStore)))

    assert(spanStore.spans.size === 100) // default is retain all
  }

  @Test def exportsStoreRateToZookeeperOnInterval() {
    result(sampler.apply(hundredSpans, Service.mk(_ => Future.Unit)))

    // Until the update interval, we'll see a store rate of zero
    assert(getLocalStoreRate === 0)

    // Await until update interval passes (1 second + fudge)
    Thread.sleep(TestAdaptiveSampler.asUpdateFreq().inMillis) // let the update interval pass

    // since update frequency is secondly, the rate exported to ZK will be the amount stored * 60
    assert(getLocalStoreRate === hundredSpans.size * 60)
  }

  @Before def clear() {
    // default to always sample
    client.setData().forPath("/sampleRate", Array[Byte]('1','.','0'))

    // remove any storage rate members
    val groupMembers = client.getChildren().forPath("/storeRates")
    if (!groupMembers.isEmpty) {
      client.setData().forPath("/storeRates/" + groupMembers.get(0), Array[Byte]('0'))
    }
  }
}

object AdaptiveSamplerTest {

  /** Makes a hundred spans, with realistic, random trace ids */
  val hundredSpans = {
    val ann = Annotation(now.inMicroseconds, Constants.ServerRecv, Some(Endpoint(127 << 24 | 1, 8080, "service")))
    val proto = Span(1L, "get", 1L, annotations = List(ann))
    new Random().longs(100).toArray.toSeq.map(id => proto.copy(traceId = id, id = id))
  }

  object TestAdaptiveSampler extends App with AdaptiveSampler

  val zookeeper = new TestingServer()
  lazy val client = newClient(zookeeper.getConnectString, new RetryOneTime(200 /* ms */))
  lazy val sampler = TestAdaptiveSampler.newAdaptiveSamplerFilter()

  @BeforeClass def beforeAll() {
    zookeeper.start()
    client.start()
    // AdaptiveSampler doesn't create these!
    client.createContainers("/election")
    client.createContainers("/storeRates")
    client.createContainers("/sampleRate")
    client.createContainers("/targetStoreRate")

    TestAdaptiveSampler.nonExitingMain(Array(
      "-zipkin.sampler.adaptive.basePath", "", // shorten for test readability
      "-zipkin.sampler.adaptive.updateFreq", "1.second", // least possible value
      "-zipkin.zookeeper.location", zookeeper.getConnectString
    ))
    ready(TestAdaptiveSampler)

    // prime zookeeper data, to make sure connection-concerns don't fail tests
    result(sampler.apply(hundredSpans, Service.mk(_ => Future.Unit)))
    Thread.sleep(TestAdaptiveSampler.asUpdateFreq().inMillis) // let the update interval pass
  }

  @AfterClass def afterAll() {
    client.close()
    zookeeper.close()
    ready(TestAdaptiveSampler.close())
  }

  /** Twitter's zookeeper group is where you store the same value as a child node */
  def getLocalStoreRate = {
    val groupMember = client.getChildren().forPath("/storeRates").get(0)
    val data = client.getData().forPath("/storeRates/" + groupMember)
    if (data.length == 0) 0 else Integer.parseInt(new String(data))
  }
}
