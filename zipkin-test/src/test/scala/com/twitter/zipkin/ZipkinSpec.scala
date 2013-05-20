/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin

import collector.ZipkinCollector
import gen.LogEntry
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import com.twitter.finagle.builder.ClientBuilder
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.zipkin.query.ZipkinQuery
import java.net.{InetSocketAddress, InetAddress}
import com.twitter.finagle.Service
import com.twitter.finagle.thrift.{ThriftClientRequest, ThriftClientFramedCodec}
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.Await

class ZipkinSpec extends Specification with JMocker with ClassMocker {

  object FakeServer extends FakeCassandra

  var collector: ZipkinCollector = null
  var collectorTransport: Service[ThriftClientRequest, Array[Byte]] = null
  var query: ZipkinQuery = null
  var queryTransport: Service[ThriftClientRequest, Array[Byte]] = null

  val mockRuntimeEnv = mock[RuntimeEnvironment]

  "ZipkinCollector and ZipkinQuery" should {
    doBefore {
      // fake cassandra node
      FakeServer.start()

      // start a collector that uses the local zookeeper and fake cassandra
      val collectorConfig = Configs.collector(FakeServer.port.get)

      // start a query service that uses the local zookeeper and fake cassandra
      val queryConfig = Configs.query(FakeServer.port.get)

      collector = collectorConfig.apply().apply(mockRuntimeEnv)
      collector.start()

      val queryStore = queryConfig.storeBuilder()
      query = new ZipkinQuery(
        new InetSocketAddress(queryConfig.serverBuilder.serverAddress, queryConfig.serverBuilder.serverPort),
        queryStore.storage,
        queryStore.index,
        queryStore.aggregates)
      query.start()

      queryTransport = ClientBuilder()
        .hosts(InetAddress.getLocalHost.getHostName + ":" + queryConfig.serverBuilder.serverPort)
        .hostConnectionLimit(1)
        .codec(ThriftClientFramedCodec())
        .build()

      collectorTransport = ClientBuilder()
        .hosts(InetAddress.getLocalHost.getHostName + ":" + collectorConfig.serverBuilder.serverPort)
        .hostConnectionLimit(1)
        .codec(ThriftClientFramedCodec())
        .build()
    }

    doAfter {
      collectorTransport.close()
      collector.shutdown()
      queryTransport.close()
      query.shutdown()

      FakeServer.stop()
    }


    "collect a trace, then return it when requested from the query daemon" in {

      val protocol = new TBinaryProtocol.Factory()
      val span = "CgABAAAAAAAAAHsLAAMAAAAGbWV0aG9kCgAEAAAAAAAAAHsKAAUAAAAAAAAAew8ABgwAA" +
        "AACCgABAAAAAAdU1MALAAIAAAACY3MMAAMIAAEBAQEBBgACAAELAAMAAAAHc2VydmljZQAACgABAAAAAA" +
        "dU1MALAAIAAAACY3IMAAMIAAEBAQEBBgACAAELAAMAAAAHc2VydmljZQAADwAIDAAAAAELAAEAAAADa2V" +
        "5CwACAAAABXZhbHVlCAADAAAAAQwABAgAAQEBAQEGAAIAAQsAAwAAAAdzZXJ2aWNlAAAA"

      // let's send off a tracing span to the collector
      val collectorClient = new gen.ZipkinCollector.FinagledClient(collectorTransport, protocol)
      Await.result(collectorClient.log(Seq(LogEntry("zipkin", span))))

      // let's check that the trace we just sent has been stored and indexed properly
      val queryClient = new gen.ZipkinQuery.FinagledClient(queryTransport, protocol)
      val traces = Await.result(queryClient.getTracesByIds(Seq(123), Seq()))
      val existSet = Await.result(queryClient.tracesExist(Seq(123, 5)))

      traces.isEmpty mustEqual false
      traces(0).spans.isEmpty mustEqual false
      traces(0).spans(0).traceId mustEqual 123

      existSet.contains(123) mustEqual true
      existSet.contains(5) mustEqual false
    }

  }
}
