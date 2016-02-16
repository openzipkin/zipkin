package com.twitter.zipkin.receiver.kafka

import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.conversions.thrift.{thriftSpanToSpan, spanToThriftSpan}
import com.twitter.zipkin.common.Span
import kafka.message.Message

class SpanCodec extends KafkaProcessor.KafkaDecoder {
  val deserializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }

  def toEvent(message: Message): List[ThriftSpan] = {
    val buffer = message.payload
    val payload = new Array[Byte](buffer.remaining)
    buffer.get(payload)
    val span = deserializer.fromBytes(payload)
    List(span)
  }
  def fromBytes(bytes: Array[Byte]): List[ThriftSpan] = List(deserializer.fromBytes(bytes))

  def encode(span: Span) = {
    val tspan = spanToThriftSpan(span)
    deserializer.toBytes(tspan.toThrift)
  }
}
