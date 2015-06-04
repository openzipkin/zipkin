package com.twitter.zipkin.receiver.kafka

import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}

/**
 * Created by aysen on 6/2/15.
 */
class KafkaDecoderImpl extends KafkaProcessor.KafkaDecoder {
  private[this] val deserializer = new FullJsonThriftSerializer[ThriftSpan] {
    val codec = ThriftSpan
  }

  override def fromBytes(bytes: Array[Byte]): Option[List[ThriftSpan]] = {
    Some(List{deserializer.fromBytes(bytes)})
  }
}


