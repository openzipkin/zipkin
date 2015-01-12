package com.twitter.zipkin.receiver.kafka

import com.twitter.zipkin.receiver.test.kafka.{TestUtils, EmbeddedZookeeper}
import com.twitter.zipkin.common._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.zipkin.conversions.thrift.{thriftSpanToSpan, spanToThriftSpan}
import com.twitter.util.{Await, Future, Promise}
import com.twitter.scrooge.BinaryThriftStructSerializer

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.junit.JUnitRunner

import kafka.consumer.{Consumer, ConsumerConnector, ConsumerConfig}
import kafka.message.Message
import kafka.producer._
import kafka.serializer.Decoder
import kafka.server.KafkaServer

import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorFactory}
import java.io._

import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.server.TwitterServer
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory

import java.util.Properties
import com.twitter.app.{App, Flaggable}
import com.twitter.zipkin.thriftscala

@RunWith(classOf[JUnitRunner])
class KafkaProcessorSpecSimple extends FunSuite with BeforeAndAfter {

  val topic = Map("integration-test-topic" -> 1)
  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
  var zkServer: EmbeddedZookeeper = _
  var testKafkaServer: KafkaServer = _
  val producerConfig = TestUtils.kafkaProducerProps
  val processorConfig = TestUtils.kafkaProcessorProps
  val decoder = new SpanDecoder()
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
    val message = Span(1234, "methodName", 4567, None, List(annotation), Nil)
    val codec = new SpanDecoder()
    codec.encode(message)
  }

  before {
    zkServer = TestUtils.startZkServer()
    Thread.sleep(500)
    testKafkaServer = TestUtils.startKafkaServer()
    Thread.sleep(500)
  }

  after {
    testKafkaServer.shutdown
    zkServer.shutdown
  }

  test("kafka processor test") {
    val producer = new Producer[Array[Byte], Array[Byte]](new ProducerConfig(producerConfig))
    val message = createMessage()
    val data = new KeyedMessage("zipkin_kafka", "any".getBytes, message)
    val recvdSpan = new Promise[Option[Seq[ThriftSpan]]]

    producer.send(data)
    producer.close()

    val service = KafkaProcessor(defaultKafkaTopics, processorConfig, { s =>
        recvdSpan.setValue(Some(s))
        Future.value(true)
      }, new SpanDecoder, new SpanDecoder)

    Await.result(recvdSpan)
    validateSpan(recvdSpan.get().getOrElse(null))
  }
}
