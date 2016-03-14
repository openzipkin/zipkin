package com.twitter.zipkin.receiver.kafka

import com.twitter.scrooge.TArrayByteTransport
import com.twitter.zipkin.conversions.thrift
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import org.apache.thrift.protocol.{TBinaryProtocol, TType}

class SpanDecoder extends KafkaProcessor.KafkaDecoder {

  // Given the thrift encoding is TBinaryProtocol..
  // .. When serializing a Span (Struct), the first byte will be the type of a field
  // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT
  // Span has no STRUCT fields: we assume that if the first byte is TType.STRUCT is a list.
  def fromBytes(bytes: Array[Byte]) =
    if (bytes(0) == TType.STRUCT) {
      thrift.thriftListToThriftSpans(bytes)
    } else {
      val proto = new TBinaryProtocol(TArrayByteTransport(bytes))
      List(ThriftSpan.decode(proto))
    }
}
