package com.twitter.zipkin.storage.redis

import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.protocol.{Limit, ZInterval}
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.storage.IndexedTraceId
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}

/**
 * @param client the redis client to use
 * @param defaultTtl expires keys older than this many seconds.
 */
abstract class TraceIndex[K](
  val client: Client,
  val defaultTtl: Option[Duration]
) extends ExpirationSupport {

  def encodeKey(key: K): ChannelBuffer

  /**
   * Adds a key into the index for the associated trace.
   *
   * @param lastTimestamp microseconds from epoch for the last annotation in a trace.
   */
  def add(key: K, lastTimestamp: Long, traceId: Long): Future[Unit] = {
    val redisKey = encodeKey(key)
    val member = ChannelBuffers.buffer(8)
    member.writeLong(traceId)
    client.zAdd(redisKey, lastTimestamp.toDouble, member)
      .flatMap(_ => expireOnTtl(redisKey))
  }

  /**
   * Returns maximum of limit trace ids from before the endTs.
   *
   * @param endTs microseconds from epoch for the youngest results.
   * @param limit maximum number of items to return
   */
  def list(key: K, endTs: Long, limit: Long): Future[Seq[IndexedTraceId]] = {
    val startTs: Long = defaultTtl map (dur => endTs - dur.inMicroseconds) getOrElse 0

    client.zRevRangeByScore(encodeKey(key), ZInterval(endTs), ZInterval(startTs), true, Some(Limit(0, limit)))
      .map(_.left.get)
      .map(_.asTuples map (tup => IndexedTraceId(tup._1.copy().readLong(), tup._2.toLong)))
  }
}
