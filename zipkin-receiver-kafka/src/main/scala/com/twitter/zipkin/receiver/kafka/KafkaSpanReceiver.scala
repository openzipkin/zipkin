package com.twitter.zipkin.receiver.kafka

import com.twitter.app.{App, Flaggable}
import java.util.Properties
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.util.{Closable, Future, Time}
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory
import kafka.serializer.Decoder

trait KafkaSpanReceiverFactory { self: App =>
  val defaultKafkaServer = "127.0.0.1:2181"
  val defaultKafkaGroupId = "zipkinId"
  val defaultKafkaZkConnectionTimeout = "1000000"
  val defaultKafkaSessionTimeout = "4000"
  val defaultKafkaSyncTime = "200"
  val defaultKafkaAutoOffset = "largest"
  val defaultKafkaTopics = Map("topic" -> 1)


  implicit object flagOfTopicProcessing extends Flaggable[Map[String, Int]] {
    def parse(s: String) = {
      val topics = s.split(",")
      val result = topics map { _.split("=") } collect { case Array(x, y, _*) => (x, y.toInt) }
      result.toMap
    }

    override def show(m: Map[String, Int]) = {
      m.mkString(";")
    }
  }

  val kafkaTopics = flag[Map[String, Int]]("zipkin.kafka.topics", defaultKafkaTopics, "kafka topics to collect from")
  val kafkaServer = flag("zipkin.kafka.server", defaultKafkaServer, "kafka server to connect")
  val kafkaGroupId = flag("zipkin.kafka.groupid", defaultKafkaGroupId, "kafka group id")
  val kafkaZkConnectionTimeout = flag("zipkin.kafka.zk.connectionTimeout", defaultKafkaZkConnectionTimeout, "kafka zk connection timeout in ms")
  val kafkaSessionTimeout = flag("zipkin.kafka.zk.sessionTimeout", defaultKafkaSessionTimeout, "kafka zk session timeout in ms")
  val kafkaSyncTime = flag("zipkin.kafka.zk.syncTime", defaultKafkaSyncTime, "kafka zk sync time in ms")
  val kafkaAutoOffset = flag("zipkin.kafka.zk.autooffset", defaultKafkaAutoOffset, "kafka zk auto offset [smallest|largest]")

  def newKafkaSpanReceiver(
    process: Seq[Span] => Future[Unit],
    stats: StatsReceiver = DefaultStatsReceiver.scope("KafkaSpanReceiver"),
    decoder: Decoder[Option[List[Span]]]
  ): SpanReceiver = new SpanReceiver {

    val props = new Properties() {
      put("groupid", kafkaGroupId())
      put("zk.connect", kafkaServer() )
      put("zk.connectiontimeout.ms", kafkaZkConnectionTimeout())
      put("zk.sessiontimeout.ms", kafkaSessionTimeout())
      put("zk.synctime.ms", kafkaSyncTime())
      put("autooffset.reset", kafkaAutoOffset())
    }

    val service = KafkaProcessor(kafkaTopics(), props, process, decoder)

    val closer: Closable = Closable.sequence(service)
    def close(deadline: Time): Future[Unit] = closeAwaitably { closer.close(deadline) }
  }

}
