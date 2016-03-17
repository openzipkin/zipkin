package com.twitter.zipkin.receiver.kafka

import com.fasterxml.jackson.core.`type`.TypeReference
import com.twitter.scrooge.TArrayByteTransport
import com.twitter.zipkin.conversions.thrift
import com.twitter.zipkin.json.{JsonSpan, ZipkinJson}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import org.apache.thrift.protocol.{TBinaryProtocol, TType}

class SpanDecoder extends KafkaProcessor.KafkaDecoder {

  val jsonSpansReader = ZipkinJson.readerFor(new TypeReference[List[JsonSpan]] {})

  // In TBinaryProtocol encoding, the first byte is the TType, in a range 0-16
  // .. If the first byte isn't in that range, it isn't a thrift.
  //
  // When byte(0) == '[' (91), assume it is a list of json-encoded spans
  //
  // When byte(0) <= 16, assume it is a TBinaryProtocol-encoded thrift
  // .. When serializing a Span (Struct), the first byte will be the type of a field
  // .. When serializing a List[ThriftSpan], the first byte is the member type, TType.STRUCT(12)
  // .. As ThriftSpan has no STRUCT fields: so, if the first byte is TType.STRUCT(12), it is a list.
  def fromBytes(bytes: Array[Byte]) =
    if (bytes(0) == '[') {
      jsonSpansReader.readValue[List[JsonSpan]](bytes).map(JsonSpan.invert)
    } else if (bytes(0) == TType.STRUCT) {
      thrift.thriftListToSpans(bytes)
    } else {
      val proto = new TBinaryProtocol(TArrayByteTransport(bytes))
      List(thrift.thriftSpanToSpan(ThriftSpan.decode(proto)).toSpan)
    }
}
