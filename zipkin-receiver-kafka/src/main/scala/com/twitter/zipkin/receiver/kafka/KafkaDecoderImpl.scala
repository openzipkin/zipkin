package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}

/**
 * Created by aysen on 6/2/15.
 */
class KafkaDecoderImpl extends KafkaProcessor.KafkaDecoder {
  private[this] val deserializer = new FullJsonThriftSerializer[ThriftSpan] {
    val codec = ThriftSpan
  }
  private[this] val log = Logger.get(getClass.getName)


  override def fromBytes(bytes: Array[Byte]): Option[List[ThriftSpan]] = {
    try {
      Some(List{deserializer.fromBytes(bytes)})
    }
    catch {
      case e : Throwable => {
        log.error(s"${e.getCause}")
        None
      }
    }
  }
}


