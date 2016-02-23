package com.twitter.zipkin.receiver.kafka

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.conversions.thrift
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import org.apache.thrift.protocol.TType

class SpanDecoder extends KafkaProcessor.KafkaDecoder {
  val deserializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }

  // Given the thrift encoding is TBinaryProtocol..
  // .. When serializing a Span (Struct), the first byte will be the type of a field
  // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT
  // Span has no STRUCT fields: we assume that if the first byte is TType.STRUCT is a list.
  def fromBytes(bytes: Array[Byte]) =
    if (bytes(0) == TType.STRUCT) {
      thrift.thriftListToThriftSpans(bytes)
    } else {
      List(deserializer.fromBytes(bytes))
    }
}
