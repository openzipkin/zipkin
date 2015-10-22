package com.twitter.zipkin.receiver.kafka

import com.github.charithe.kafka.KafkaJunitRule
import com.twitter.util.{Await, Future, Promise}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift.thriftSpanToSpan
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.producer._
import org.junit.{ClassRule, Test}
import org.scalatest.junit.JUnitSuite

object KafkaProcessorSpecSimple {
  // Singleton as the test needs to read the actual port in use
  val kafkaRule = new KafkaJunitRule()

  // Scala cannot generate fields with public visibility, so use a def instead.
  @ClassRule def kafkaRuleDef = kafkaRule
}

class KafkaProcessorSpecSimple extends JUnitSuite {

  import KafkaProcessorSpecSimple.kafkaRule

  val topic = Map("integration-test-topic" -> 1)
  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)))
  val decoder = new SpanCodec()
  val defaultKafkaTopics = Map("zipkin_kafka" -> 1 )

  def validateSpan(spans: Seq[ThriftSpan]): Future[Unit] = {
    assert( 1 == spans.length, "received more spans than sent" )
    val message = spans.head.toSpan
    assert(message.traceId == 1234, "traceId mismatch")
    assert(message.name == "methodName", "method name mismatch")
    assert(message.id == 4567, "spanId mismatch")
    message.annotations map { a =>
      assert(a.value == "value", "annotation name mismatch")
      assert(a.timestamp == 1, "annotation timestamp mismatch")
    }
    Future.Done
  }

  def createMessage(): Array[Byte] = {
    val annotation = Annotation(1, "value", Some(Endpoint(1, 2, "service")))
    val message = Span(1234, "methodName", 4567, None, List(annotation))
    val codec = new SpanCodec()
    codec.encode(message)
  }

  @Test def kafkaProcessorTest() {
    val producer = new Producer[Array[Byte], Array[Byte]](kafkaRule.producerConfigWithDefaultEncoder())
    val message = createMessage()
    val data = new KeyedMessage("zipkin_kafka", "any".getBytes, message)
    val recvdSpan = new Promise[Seq[ThriftSpan]]

    producer.send(data)
    producer.close()

    val service = KafkaProcessor(defaultKafkaTopics, kafkaRule.consumerConfig(), { s =>
        recvdSpan.setValue(s)
        Future.value(true)
      }, new SpanCodec, new SpanCodec)

    validateSpan(Await.result(recvdSpan))
  }
}
