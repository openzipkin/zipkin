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
import kafka.utils.SystemTime

object TestUtils {

  val seededRandom = new Random(192348092834L)
  val random = new Random
  val zookeeperConnect = "localhost:2181"

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
      put("host.name", "localhost")
      put("port", ports(0).toString)
      put("broker.id", "0")
      put("log.dirs", String.valueOf(logDir))
      put("zookeeper.connect", zookeeperConnect)
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
    put("producer.type"       , "sync")
    put("metadata.broker.list", "localhost:" + ports(0).toString)
    put("partitioner.class"   , "kafka.producer.DefaultPartitioner")
    put("serializer.class"    , "kafka.serializer.DefaultEncoder");
  }

  def kafkaProcessorProps = new Properties() {
    put("group.id"                    , "zipkinId")
    put("zookeeper.connect"           , "localhost:2181")
    put("auto.offset.reset"           , "smallest")
    put("zookeeper.session.timeout.ms", "400")
    put("zookeeper.sync.time.ms"      , "200")
    put("auto.commit.interval.ms"     , "1000")
  }

}

class EmbeddedZookeeper(connectString: String) {
  val dataDir = TestUtils.tempDir()
  val port = connectString.split(":")(1).toInt

  val zkProps = new Properties {
    put("dataDir"      , String.valueOf(dataDir))
    put("clientPort"   , String.valueOf(port))
    put("tickTime"     , "2000")
    put("maxClientCnxs", "100")
  }

  val zk                      = new ZooKeeperServerMain()
  val conf: ServerConfig      = new ServerConfig()
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
