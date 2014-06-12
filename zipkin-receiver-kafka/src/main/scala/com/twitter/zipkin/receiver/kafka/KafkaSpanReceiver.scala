package com.twitter.zipkin.receiver.kafka

import com.twitter.app.{App, Flaggable}
import java.util.Properties
import com.twitter.zipkin.gen.{Span => ThriftSpan}
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.conversions.thrift._
import com.twitter.util.{Closable, Future, Time}
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory

trait KafkaSpanReceiverFactory { self: App =>
  val defaultKafkaServer = "127.0.0.1:2181"
  val defaultKafkaGroupId = "zipkinId"
  val defaultKafkaZkConnectionTimeout = "1000000"
  val defaultKafkaSessionTimeout = "4000"
  val defaultKafkaSyncTime = "200"
  val defaultKafkaAutoOffset = "largest"
  val defaultKafkaTopics = Map("topic" -> 1)

  val kafkaTopics = flag[Map[String, Int]]("zipkin.kafka.topics", defaultKafkaTopics, "kafka topics to collect from")
  val kafkaServer = flag("zipkin.kafka.server", defaultKafkaServer, "kafka server to connect")
  val kafkaGroupId = flag("zipkin.kafka.groupid", defaultKafkaGroupId, "kafka group id")
  val kafkaZkConnectionTimeout = flag("zipkin.kafka.zk.connectionTimeout", defaultKafkaZkConnectionTimeout, "kafka zk connection timeout in ms")
  val kafkaSessionTimeout = flag("zipkin.kafka.zk.sessionTimeout", defaultKafkaSessionTimeout, "kafka zk session timeout in ms")
  val kafkaSyncTime = flag("zipkin.kafka.zk.syncTime", defaultKafkaSyncTime, "kafka zk sync time in ms")
  val kafkaAutoOffset = flag("zipkin.kafka.zk.autooffset", defaultKafkaAutoOffset, "kafka zk auto offset [smallest|largest]")

  def newKafkaSpanReceiver(
    process: Seq[ThriftSpan] => Future[Unit],
    stats: StatsReceiver = DefaultStatsReceiver.scope("KafkaSpanReceiver"),
    decoder: KafkaProcessor.KafkaDecoder
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

    def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.sequence(service).close(deadline)
    }
  }

}
