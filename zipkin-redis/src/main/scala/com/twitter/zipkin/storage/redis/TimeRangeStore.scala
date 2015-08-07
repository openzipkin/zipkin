package com.twitter.zipkin.storage.redis

import com.twitter.finagle.redis.Client
import com.twitter.util.Future
import java.nio.charset.Charset
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

/**
 * @param client the redis client to use
 * @param name namespace/redis hash key
 */
class TimeRangeStore(client: Client, name: String) {
  val key = ChannelBuffers.copiedBuffer(name, Charset.defaultCharset)

  /**
   * Replaces the duration of a trace.
   */
  def put(traceId: Long, range: TimeRange): Future[Unit] = {
    client.hSet(key, long(traceId), timeRange(range)).unit
  }

  /**
   * Returns the duration of a trace by id.
   */
  def get(traceId: Long): Future[Option[TimeRange]] =
    client.hGet(key, long(traceId)).map(buf => buf.map(timeRange(_)))

  private def timeRange(buf: ChannelBuffer) = TimeRange(buf.readLong(), buf.readLong())

  private def timeRange(timeRange: TimeRange) = {
    val buf = ChannelBuffers.buffer(16)
    buf.writeLong(timeRange.startTs)
    buf.writeLong(timeRange.stopTs)
    buf
  }

  private def long(long: Long): ChannelBuffer = {
    val buf = ChannelBuffers.buffer(8)
    buf.writeLong(long)
    buf
  }
}
