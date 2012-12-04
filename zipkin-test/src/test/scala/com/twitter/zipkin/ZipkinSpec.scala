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
import com.twitter.io.TempFile
import com.twitter.finagle.builder.ClientBuilder
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.util.Eval
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.zipkin.query.ZipkinQuery
import com.twitter.common.zookeeper.ServerSet
import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor
import java.net.{InetSocketAddress, InetAddress}
import java.util.Map
import com.twitter.thrift.{Status, ServiceInstance}
import com.twitter.zipkin.config.{ZipkinQueryConfig, CassandraStorageConfig, ZipkinCollectorConfig}
import org.apache.zookeeper.server.persistence.FileTxnSnapLog
import org.apache.zookeeper.server.ZooKeeperServer.BasicDataTreeBuilder
import org.apache.zookeeper.server.{NIOServerCnxn, ZooKeeperServer}
import com.twitter.common.io.FileUtils
import com.twitter.finagle.Service
import com.twitter.finagle.thrift.{ThriftClientRequest, ThriftClientFramedCodec}

class ZipkinSpec extends Specification with JMocker with ClassMocker {

  object FakeServer extends FakeCassandra

  var collector: ZipkinCollector = null
  var collectorTransport: Service[ThriftClientRequest, Array[Byte]] = null
  var query: ZipkinQuery = null
  var queryTransport: Service[ThriftClientRequest, Array[Byte]] = null
  var zooKeeperServer: ZooKeeperServer = null
  var connectionFactory: NIOServerCnxn.Factory = null

  "ZipkinCollector and ZipkinQuery" should {
    doBefore {
      // fake cassandra node
      FakeServer.start()

      // start a temporary zookeeper server
      val zkPort = 2181 // TODO pick another port?
      val tmpDir = FileUtils.createTempDir()
      zooKeeperServer =
        new ZooKeeperServer(new FileTxnSnapLog(tmpDir, tmpDir), new BasicDataTreeBuilder())
      connectionFactory = new NIOServerCnxn.Factory(new InetSocketAddress(zkPort))
      connectionFactory.startup(zooKeeperServer)

      // no need to register in serversets
      val nullServerSetsImpl = new ServerSet() {
        def join(p1: InetSocketAddress, p2: Map[String, InetSocketAddress], p3: Status) = null
        def monitor(p1: HostChangeMonitor[ServiceInstance]) {}
      }

      // start a collector that uses the local zookeeper and fake cassandra
      val collectorConfigFile = TempFile.fromResourcePath("/TestCollector.scala")
      val collectorConfig = new Eval().apply[ZipkinCollectorConfig](collectorConfigFile)
      collectorConfig.zkConfig.servers = List("localhost:" + zkPort)
      collectorConfig.storageConfig.asInstanceOf[CassandraStorageConfig].cassandraConfig.port = FakeServer.port.get
      val collectorPort = collectorConfig.serverPort

      // start a query service that uses the local zookeeper and fake cassandra
      val queryFile = TempFile.fromResourcePath("/TestQuery.scala")
      val queryConfig = new Eval().apply[ZipkinQueryConfig](queryFile)
      queryConfig.zkConfig.servers = List("localhost:" + zkPort)
      queryConfig.storageConfig.asInstanceOf[CassandraStorageConfig].cassandraConfig.port = FakeServer.port.get
      val queryPort = queryConfig.serverPort

      collector = new ZipkinCollector(collectorConfig)
      collector.start()

      query = new ZipkinQuery(queryConfig, nullServerSetsImpl, queryConfig.storage, queryConfig.index, queryConfig.aggregates)
      query.start()

      queryTransport = ClientBuilder()
        .hosts(InetAddress.getLocalHost.getHostName + ":" + queryPort)
        .hostConnectionLimit(1)
        .codec(ThriftClientFramedCodec())
        .build()

      collectorTransport = ClientBuilder()
        .hosts(InetAddress.getLocalHost.getHostName + ":" + collectorPort)
        .hostConnectionLimit(1)
        .codec(ThriftClientFramedCodec())
        .build()
    }

    doAfter {
      collectorTransport.release()
      collector.shutdown()
      queryTransport.release()
      query.shutdown()

      zooKeeperServer.shutdown()
      connectionFactory.shutdown()

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
      collectorClient.log(Seq(LogEntry("zipkin", span)))()

      // let's check that the trace we just sent has been stored and indexed properly
      val queryClient = new gen.ZipkinQuery.FinagledClient(queryTransport, protocol)
      val traces = queryClient.getTracesByIds(Seq(123), Seq())()
      val existSet = queryClient.tracesExist(Seq(123, 5))()

      traces.isEmpty mustEqual false
      traces(0).spans.isEmpty mustEqual false
      traces(0).spans(0).traceId mustEqual 123

      existSet.contains(123) mustEqual true
      existSet.contains(5) mustEqual false
    }

  }
}
