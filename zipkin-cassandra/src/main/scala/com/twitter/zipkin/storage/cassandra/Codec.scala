package com.twitter.zipkin.storage.cassandra

import java.nio.ByteBuffer

/**
 * A bidirection encoding for column names or values.
 */
trait Codec[A] {
  def encode(obj: A): ByteBuffer
  def decode(ary: ByteBuffer): A

  /** To conveniently get the singleton/Object from Java. */
  def get() = this

  /** Helpers for conversion from ByteBuffers to byte arrays. Keep explicit! */
  def b2b(buff: ByteBuffer): Array[Byte] = {
    val bytes = new Array[Byte](buff.remaining)
    buff.duplicate.get(bytes)
    bytes
  }
  def b2b(array: Array[Byte]): ByteBuffer = ByteBuffer.wrap(array)
}
