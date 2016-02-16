package com.twitter.zipkin.receiver.kafka

import java.util.concurrent.LinkedBlockingQueue

import com.github.charithe.kafka.KafkaJunitRule
import com.twitter.util.{Await, Future, Promise}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.producer._
import org.junit.{ClassRule, Test}
import org.scalatest.junit.JUnitSuite

object KafkaProcessorSpec {
  // Singleton as the test needs to read the actual port in use
  val kafkaRule = new KafkaJunitRule()

  // Scala cannot generate fields with public visibility, so use a def instead.
  @ClassRule def kafkaRuleDef = kafkaRule
}

class KafkaProcessorSpec extends JUnitSuite {

  import KafkaProcessorSpec.kafkaRule

  val annotation = Annotation(1, "value", Some(Endpoint(1, 2, "service")))
  // Intentionally leaving timestamp and duration unset, as legacy instrumentation don't set this.
  val span = Span(1234, "methodname", 4567, annotations = List(annotation))
  val codec = new SpanCodec()

  @Test def messageWithSingleSpan() {
    val topic = "single_span"
    val recvdSpan = new Promise[Seq[ThriftSpan]]

    val service = KafkaProcessor(Map(topic -> 1), kafkaRule.consumerConfig(), { s =>
      recvdSpan.setValue(s)
      Future.value(true)
    }, codec, codec)

    val producer = new Producer[Array[Byte], Array[Byte]](kafkaRule.producerConfigWithDefaultEncoder())
    producer.send(new KeyedMessage(topic, codec.encode(span)))
    producer.close()

    assert(Await.result(recvdSpan) == Seq(span.toThrift))

    Await.result(service.close())
  }

  @Test def skipsMalformedData() {
    val topic = "malformed"
    val recvdSpans = new LinkedBlockingQueue[Seq[ThriftSpan]](3)

    val service = KafkaProcessor(Map(topic -> 1), kafkaRule.consumerConfig(), { s =>
      recvdSpans.add(s)
      Future.value(true)
    }, codec, codec)

    val producer = new Producer[Array[Byte], Array[Byte]](kafkaRule.producerConfigWithDefaultEncoder())

    producer.send(new KeyedMessage(topic, codec.encode(span)))
    producer.send(new KeyedMessage(topic, "malformed".getBytes()))
    producer.send(new KeyedMessage(topic, codec.encode(span)))
    producer.close()

    for (elem <- 1 until 2)
      assert(recvdSpans.take() == Seq(span.toThrift))

    Await.result(service.close())
  }
}
