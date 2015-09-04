package com.twitter.zipkin.receiver.kafka

import java.util.Properties

import com.twitter.app.App
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.{Closable, Future, Time}
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.consumer.ConsumerConfig
import kafka.serializer.Decoder

object KafkaSpanReceiverFactory {

  /**
   * Returns a factory function which can be applied to [[SpanReceiver.Processor]].
   *
   * @param zookeeper the zookeeper connect string, ex. 127.0.0.1:2181
   * @param topic  the topic zipkin spans will be consumed from
   */
  def factory(zookeeper: String, topic: String) = {
    object KafkaFactory extends App with KafkaSpanReceiverFactory
    KafkaFactory.kafkaZookeeperConnect.parse(zookeeper)
    KafkaFactory.kafkaTopics.parse(topic + "=1")
    (process: SpanReceiver.Processor) => KafkaFactory.newKafkaSpanReceiver(process)
  }
}
trait KafkaSpanReceiverFactory { self: App =>
  val defaultKafkaServer = "127.0.0.1:2181"
  val defaultKafkaGroupId = "zipkin"
  val defaultKafkaZkConnectionTimeout = "1000000"
  val defaultKafkaSessionTimeout = "4000"
  val defaultKafkaSyncTime = "200"
  val defaultKafkaAutoOffset = "smallest"
  val defaultKafkaTopics = Map("zipkin" -> 1)

  val kafkaTopics = flag[Map[String, Int]]("zipkin.kafka.topics", defaultKafkaTopics, "kafka topics to collect from")
  val kafkaZookeeperConnect = flag("zipkin.kafka.server", defaultKafkaServer, "kafka zk connect string")
  val kafkaGroupId = flag("zipkin.kafka.groupid", defaultKafkaGroupId, "kafka group id")
  val kafkaZkConnectionTimeout = flag("zipkin.kafka.zk.connectionTimeout", defaultKafkaZkConnectionTimeout, "kafka zk connection timeout in ms")
  val kafkaSessionTimeout = flag("zipkin.kafka.zk.sessionTimeout", defaultKafkaSessionTimeout, "kafka zk session timeout in ms")
  val kafkaSyncTime = flag("zipkin.kafka.zk.syncTime", defaultKafkaSyncTime, "kafka zk sync time in ms")
  val kafkaAutoOffset = flag("zipkin.kafka.zk.autooffset", defaultKafkaAutoOffset, "kafka zk auto offset [smallest|largest]")

  def newKafkaSpanReceiver[T](
    process: Seq[ThriftSpan] => Future[Unit],
    stats: StatsReceiver = DefaultStatsReceiver.scope("KafkaSpanReceiver"),
    keyDecoder: Decoder[T] = KafkaProcessor.defaultKeyDecoder,
    valueDecoder: KafkaProcessor.KafkaDecoder = new SpanCodec()
  ): SpanReceiver = new SpanReceiver {


    val receiverProps = new Properties() {
      put("group.id", kafkaGroupId())
      put("zookeeper.connect", kafkaZookeeperConnect() )
      put("zookeeper.connection.timeout.ms", kafkaZkConnectionTimeout())
      put("zookeeper.session.timeout.ms", kafkaSessionTimeout())
      put("zookeeper.sync.time.ms", kafkaSyncTime())
      put("auto.offset.reset", kafkaAutoOffset())
      put("auto.commit.interval.ms", "10")
      put("consumer.id", "zipkin")
      put("consumer.timeout.ms", "-1")
      put("rebalance.max.retries", "4")
      put("num.consumer.fetchers", "2")
    }

    val service = KafkaProcessor(kafkaTopics(), new ConsumerConfig(receiverProps), process, keyDecoder, valueDecoder)

    def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.sequence(service).close(deadline)
    }
  }

}
