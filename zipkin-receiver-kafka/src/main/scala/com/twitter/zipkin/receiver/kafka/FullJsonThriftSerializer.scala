package com.twitter.zipkin.receiver.kafka

import com.twitter.scrooge.{ThriftStructCodec, ThriftStructSerializer, ThriftStruct}

/**
 * Created by aysen on 6/4/15.
 */
trait FullJsonThriftSerializer[T <: ThriftStruct] extends ThriftStructSerializer[T] {
  override def encoder = com.twitter.util.StringEncoder

  val protocolFactory = new org.apache.thrift.protocol.TJSONProtocol.Factory
}

object FullJsonThriftSerializer {
  def apply[T <: ThriftStruct](_codec: ThriftStructCodec[T]): FullJsonThriftSerializer[T] =
    new FullJsonThriftSerializer[T] {
      def codec = _codec
    }
}

