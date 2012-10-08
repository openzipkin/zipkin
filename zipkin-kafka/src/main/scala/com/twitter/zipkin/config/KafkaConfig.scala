package com.twitter.zipkin.config

import com.twitter.finagle.stats.{StatsReceiver, NullStatsReceiver}
import com.twitter.util.Config
import com.twitter.zipkin.collector.Kafka
import com.twitter.zipkin.gen
import java.util.Properties
import kafka.producer.Producer
import kafka.producer.ProducerConfig

trait KafkaConfig extends Config[Kafka] {
  var zkConnectString: String = "localhost:2181"
  var topic: String = "zipkin"
  var statsReceiver: StatsReceiver = NullStatsReceiver

  def apply(): Kafka = {
    val properties = new Properties
    properties.put("zk.connect", zkConnectString)
    properties.put("serializer.class", "com.twitter.zipkin.collector.SpanEncoder")
    properties.put("producer.type", "sync")
    val producer = new Producer[String, gen.Span](new ProducerConfig(properties))
    new Kafka(producer, topic, statsReceiver.scope("kafka"))
  }
}
