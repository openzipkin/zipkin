package com.twitter.zipkin.collector

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.util.Future
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import kafka.message.Message
import kafka.producer.{ProducerData, Producer}
import kafka.serializer.Encoder

class Kafka(
  kafka: Producer[String, gen.Span],
  topic: String,
  statsReceiver: StatsReceiver
) extends Service[Span, Unit] {

  private[this] val log = Logger.get()

  def apply(req: Span): Future[Unit] = {
    statsReceiver.counter("try").incr()
    val producerData = new ProducerData[String, gen.Span](topic, Seq(req.toThrift))
    Future {
      kafka.send(producerData)
    } onSuccess { (_) =>
      statsReceiver.counter("success").incr()
    }
  }

  override def release() {
    kafka.close()
  }
}

class SpanEncoder extends Encoder[gen.Span] {
  val serializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  def toMessage(span: gen.Span): Message = {
    new Message(serializer.toBytes(span))
  }
}
