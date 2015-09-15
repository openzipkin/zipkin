package com.twitter.zipkin.storage.redis

import com.twitter.scrooge.{ThriftStruct, ThriftStructSerializer}
import org.iq80.snappy.Snappy
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

/**
 * Snappy serializer and deserializer for thrift structs
 */
class RedisSnappyThriftCodec[T <: ThriftStruct](val serializer: ThriftStructSerializer[T]) {

  def encode(t: T): ChannelBuffer = {
    val arr = serializer.toBytes(t)
    val compressArr = new Array[Byte](Snappy.maxCompressedLength(arr.length))
    val compressLen = Snappy.compress(arr, 0, arr.length, compressArr, 0)
    ChannelBuffers.copiedBuffer(compressArr, 0, compressLen)
  }

  def decode(cb: ChannelBuffer): T = {
    val compressedArr = new Array[Byte](cb.capacity())
    cb.readBytes(compressedArr)
    val uncompressedArr = Snappy.uncompress(compressedArr, 0, compressedArr.length)
    serializer.fromBytes(uncompressedArr)
  }

}
