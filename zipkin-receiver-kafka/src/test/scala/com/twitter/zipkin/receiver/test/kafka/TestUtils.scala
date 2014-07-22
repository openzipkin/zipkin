package com.twitter.zipkin.receiver.test.kafka

import java.io.File
import java.util.Random
import java.net.{ServerSocket, InetSocketAddress}
import java.util.Properties

import kafka.server.{KafkaServer, KafkaConfig}
import kafka.utils.{Utils, ZKStringSerializer}

import org.I0Itec.zkclient.ZkClient
import org.apache.commons.io.FileUtils
import org.apache.zookeeper.server.{ZooKeeperServerMain, ServerConfig}
import org.apache.zookeeper.server.NIOServerCnxn
import org.apache.zookeeper.server.quorum.QuorumPeerConfig

object TestUtils {

  val seededRandom = new Random(192348092834L)
  val random = new Random
  val zookeeperConnect = "127.0.0.1:2182"


  // 0 - kafka, 1 - zk
  val ports = choosePorts(2)

  def choosePorts(count: Int) = {
    val sockets =
      for(i <- 0 until count)
        yield new ServerSocket(0)
    val socketList = sockets.toList
    val ports = socketList.map(_.getLocalPort)
    ports
  }

  def choosePort(): Int = choosePorts(1).head

  def tempDir(): File = {
    val ioDir = System.getProperty("java.io.tempdir")
    val f = new File(ioDir, "kafka-" + random.nextInt(10000000))
    f.mkdirs
    Runtime.getRuntime.addShutdownHook(new Thread { override def run() {
      FileUtils.deleteDirectory(f)
    }})
    f
  }

  def startKafkaServer() = {
    val logDir = tempDir()
    val props = new Properties() {
      put("hostname", "127.0.0.1")
      put("port", ports(0).toString)
      put("brokerid", "1")
      put("log.dir", String.valueOf(logDir))
      put("enable.zookeeper", "true")
      put("zk.connect", zookeeperConnect)
    }
    val kafkaServer = new KafkaServer(new KafkaConfig(props))
    kafkaServer.startup
    kafkaServer
  }


  def startZkServer() = {
    val kafkaZkServer = new EmbeddedZookeeper(zookeeperConnect)
    kafkaZkServer
  }

  def kafkaProducerProps = new Properties() {
    put("producer.type", "sync")
    put("broker.list", "1:127.0.0.1:" + ports(0).toString)
  }

  def kafkaProcessorProps = new Properties() {
    put("groupid", "unit-test-id")
    put("zk.connect", "127.0.0.1:2182")
    put("autooffset.reset", "largest")
    put("consumerid", "consumerid")
    put("consumer.timeout.ms", "-1")
  }

}

class EmbeddedZookeeper(connectString: String) {
  val dataDir = TestUtils.tempDir()
  val port = connectString.split(":")(1).toInt

  val zkProps = new Properties {
    put("dataDir", String.valueOf(dataDir))
    put("clientPort", String.valueOf(port))
    put("tickTime", "2000")
    put("maxClientCnxs", "100")
  }

  val zk = new ZooKeeperServerMain()
  val conf: ServerConfig = new ServerConfig()
  val qConf: QuorumPeerConfig = new QuorumPeerConfig()
  qConf.parseProperties(zkProps)
  conf.readFrom(qConf)

  new Thread( new Runnable {
    def run() {
      zk.runFromConfig(conf)
    }
  }).start

  val client = new ZkClient(connectString)
  client.setZkSerializer(ZKStringSerializer)

  def shutdown() {
    Utils.rm(dataDir)
  }
}
