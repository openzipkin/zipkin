/*
 * Copyright 2012 Tumblr Inc.
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

package com.twitter.zipkin.storage

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift.{spanToThriftSpan,thriftSpanToSpan}
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.storage.redis.{ExpiringValue, TimeRange}
import java.nio.charset.Charset
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

/**
 * Useful conversions for encoding/decoding.
 */
package object redis {
  private[redis] implicit def encodeStartEnd(timeRange: TimeRange): ChannelBuffer = {
    val buf = ChannelBuffers.buffer(16)
    buf.writeLong(timeRange.first)
    buf.writeLong(timeRange.last)
    buf
  }

  private[redis] implicit def decodeStartEnd(buf: ChannelBuffer): TimeRange =
    TimeRange(buf.readLong(), buf.readLong())


  private[redis] implicit def expiringValue2ChanBuf[A](log: ExpiringValue[A])(implicit convert: Function[A, ChannelBuffer]): ChannelBuffer = {
    val buf = ChannelBuffers.buffer(16)
    buf.writeLong(log.expiresAt.inSeconds.toLong)
    buf.writeBytes(convert(log.value))
    buf
  }

  private[redis] implicit def chanBuf2ExpiringValue[A](buffer: ChannelBuffer)(implicit convert: Function[ChannelBuffer, A]): ExpiringValue[A] = {
    val tmp = buffer.copy()
    val expiresAt = tmp.readLong()
    val value = tmp.copy()
    ExpiringValue(expiresAt, convert(value))
  }

  private[redis] implicit def longToString(long: Long) = long.toString

  private[redis] implicit def chanBuf2Long(buf: ChannelBuffer): Long =
    buf.copy().readLong()

  private[redis] implicit def long2ChanBuf(long: Long): ChannelBuffer = {
    val buf = ChannelBuffers.buffer(8)
    buf.writeLong(long)
    buf
  }

  private[redis] implicit def double2ChanBuf(double: Double): ChannelBuffer = {
    val buf = ChannelBuffers.buffer(8)
    buf.writeDouble(double)
    buf
  }

  private[redis] implicit def chanBuf2Double(buf: ChannelBuffer): Double =
    buf.copy().readDouble()

  private[redis] implicit def string2ChanBuf(string: String): ChannelBuffer = ChannelBuffers.copiedBuffer(string, Charset.defaultCharset)

  private[redis] implicit def chanBuf2String(buf: ChannelBuffer) = buf.toString(Charset.defaultCharset)

  val serializer = new BinaryThriftStructSerializer[thriftscala.Span] {
    def codec = thriftscala.Span
  }

  private[redis] implicit def serializeSpan(span: Span): ChannelBuffer = {
    val tmp = span.toThrift
    val bytes = serializer.toBytes(tmp)
    ChannelBuffers.copiedBuffer(bytes)
  }

  private[redis] implicit def deserializeSpan(buf: ChannelBuffer): Span =
    serializer.fromBytes(buf.copy().array).toSpan

}
