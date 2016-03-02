package com.twitter.zipkin.receiver.kafka

import java.util.concurrent.LinkedBlockingQueue

import com.github.charithe.kafka.KafkaJunitRule
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Promise}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.producer._
import org.apache.thrift.protocol.{TType, TList, TBinaryProtocol}
import org.apache.thrift.transport.TMemoryBuffer
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
  val codec = new SpanDecoder()

  @Test def messageWithSingleSpan() {
    val topic = "single_span"
    val recvdSpan = new Promise[Seq[ThriftSpan]]

    val service = KafkaProcessor(Map(topic -> 1), kafkaRule.consumerConfig(), { s =>
      recvdSpan.setValue(s)
      Future.value(true)
    }, codec, codec)

    val producer = new Producer[Array[Byte], Array[Byte]](kafkaRule.producerConfigWithDefaultEncoder())
    producer.send(new KeyedMessage(topic, encode(span)))
    producer.close()

    assert(Await.result(recvdSpan) == Seq(span.toThrift))

    Await.result(service.close())
  }

  @Test def messageWithMultipleSpans() {
    val topic = "multiple_spans"
    val recvdSpan = new Promise[Seq[ThriftSpan]]

    val service = KafkaProcessor(Map(topic -> 1), kafkaRule.consumerConfig(), { s =>
      recvdSpan.setValue(s)
      Future.value(true)
    }, codec, codec)

    val producer = new Producer[Array[Byte], Array[Byte]](kafkaRule.producerConfigWithDefaultEncoder())
    producer.send(new KeyedMessage(topic, encode(Seq(span, span)))) // 2 spans in one message
    producer.close()

    // make sure we decoded both spans from the same message
    assert(Await.result(recvdSpan) == Seq(span.toThrift, span.toThrift))

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

    producer.send(new KeyedMessage(topic, encode(span)))
    producer.send(new KeyedMessage(topic, "malformed".getBytes()))
    producer.send(new KeyedMessage(topic, encode(span)))
    producer.close()

    for (elem <- 1 until 2)
      assert(recvdSpans.take() == Seq(span.toThrift))

    Await.result(service.close())
  }

  def encode(span: Span) = {
    val transport = new TMemoryBuffer(0)
    val oproto = new TBinaryProtocol(transport)
    val tspan = spanToThriftSpan(span)
    tspan.toThrift.write(oproto)
    transport.getArray()
  }

  def encode(spans: Seq[Span]) = {
    // serialize all spans as a thrift list
    val transport = new TMemoryBuffer(0)
    val oproto = new TBinaryProtocol(transport)
    oproto.writeListBegin(new TList(TType.STRUCT, spans.size))
    spans.map(spanToThriftSpan).foreach(_.toThrift.write(oproto))
    oproto.writeListEnd()
    transport.getArray()
  }
}
