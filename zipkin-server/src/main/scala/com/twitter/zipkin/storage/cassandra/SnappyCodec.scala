/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.storage.cassandra

import java.nio.ByteBuffer
import com.twitter.cassie.codecs.Codec
import com.twitter.zipkin.util.Util
import org.iq80.snappy.Snappy
import com.twitter.ostrich.stats.Stats

/**
 * Cassie codec that wraps another and compresses/decompresses that data.
 * TODO remove this once we are running a Cassandra version with internal compression.
 *
 * FIXME very inefficient with multiple copies all over the place
 * would be nice with straight bytebuffer support in Snappy but this will do in the mean time
 */
class SnappyCodec[T](codec: Codec[T]) extends Codec[T] {

  def encode(t: T): ByteBuffer = {
    Stats.time("snappycodec.compress") {
      val arr = Util.getArrayFromBuffer(codec.encode(t))
      val compressArr = new Array[Byte](Snappy.maxCompressedLength(arr.length))
      val compressLen = Snappy.compress(arr, 0, arr.length, compressArr, 0)
      Stats.addMetric("snappycodec.compressed", compressLen)
      ByteBuffer.wrap(compressArr, 0, compressLen)
    }
  }

  def decode(ary: ByteBuffer): T = {
    Stats.time("snappycodec.decompress") {
      val arr = Util.getArrayFromBuffer(ary)
      val uncompressedArr = Snappy.uncompress(arr, 0, arr.length)
      codec.decode(ByteBuffer.wrap(uncompressedArr))
    }
  }
}
