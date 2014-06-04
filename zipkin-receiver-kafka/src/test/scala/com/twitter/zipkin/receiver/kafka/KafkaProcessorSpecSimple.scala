package com.twitter.zipkin.receiver.kafka


import com.twitter.util.{Await, Future}
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Endpoint, Span} 
import com.twitter.zipkin.conversions.thrift.{thriftSpanToSpan, spanToThriftSpan}
import com.twitter.zipkin.gen
import com.twitter.zipkin.receiver.test.kafka.{TestUtils, EmbeddedZookeeper}

import kafka.server.KafkaServer
import kafka.message.Message
import kafka.producer.{Producer, ProducerConfig, ProducerData}
import kafka.consumer.{Consumer, ConsumerConnector, ConsumerConfig}
import kafka.serializer.Decoder

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class KafkaProcessorSpecSimple extends FunSuite with BeforeAndAfter {

  case class TestDecoder extends KafkaDecoder {
      val deserializer = new BinaryThriftStructSerializer[gen.Span] {
          def codec = gen.Span
      }

      def toEvent(message: Message): Option[List[Span]] = {

        val buffer = message.payload
        val payload = new Array[Byte](buffer.remaining)
        buffer.get(payload)

        val gSpan = deserializer.fromBytes(payload)
        val span = thriftSpanToSpan(gSpan).toSpan
        Some(List(span))
      }

      def encode(span: Span) = {
        val gspan = spanToThriftSpan(span)
        deserializer.toBytes(gspan.toThrift)
      }
  }

  var zkServer: EmbeddedZookeeper = _
  var kafkaServer: KafkaServer = _

  def processorFun(spans: Seq[Span]): Future[Unit] = {
    assert(1 == spans.length, "received more spans than sent")
    val message = spans.head
    assert(message.traceId == 1234, "traceId mismatch")
    assert(message.name == "methodName", "method name mismatch")
    assert(message.id == 4567, "spanId mismatch")
    message.annotations map {
      a => {
        assert(a.value == "value", "annotation name mismatch")
        assert(a.timestamp == 1, "annotation timestamp mismatch")
      }
    }
    Future.Done
  }

  def createMessage(): Message = {
    val annotation = Annotation(1, "value", Some(Endpoint(1, 2, "service")))
    val message = Span(1234, "methodName", 4567, None, List(annotation), Nil)
    val codec = new TestDecoder()
    new Message(codec.encode(message))
  }

  before {
    zkServer = TestUtils.startZkServer()
    kafkaServer = TestUtils.startKafkaServer()
    Thread.sleep(500)
  }

  after {
    kafkaServer.shutdown
    zkServer.shutdown
  }

  test("kafka processor test simple") {

    val producerConfig = TestUtils.kafkaProducerProps
    val processorConfig = TestUtils.kafkaProcessorProps
    val producer = new Producer[String, Message](new ProducerConfig(producerConfig))
    val message = createMessage()
    val data = new ProducerData[String, Message]("integration-test-topic", "key", Seq(message) )

    val decoder = new TestDecoder()
    producer.send(data)
    producer.close()

    val topic = Map("integration-test-topic" -> 1)
    val consumerConnector: ConsumerConnector = Consumer.create(new ConsumerConfig(processorConfig))
    val topicMessageStreams = consumerConnector.createMessageStreams(topic, decoder)

    for ((topic, streams) <- topicMessageStreams) {
      val messageList = streams.head.head.message getOrElse List()
      processorFun(messageList)
    }
  }

}
