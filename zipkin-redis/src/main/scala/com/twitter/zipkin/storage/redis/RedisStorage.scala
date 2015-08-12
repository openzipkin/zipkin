package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets._
import com.twitter.finagle.redis.Client
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift.{ThriftSpan, WrappedSpan}
import com.twitter.zipkin.thriftscala
import java.io.Closeable
import org.jboss.netty.buffer.ChannelBuffers._
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

/**
 * @param client the redis client to use
 * @param defaultTtl expires keys older than this many seconds.
 */
class RedisStorage(
  val client: Client,
  val defaultTtl: Option[Duration]
) extends Closeable with ExpirationSupport {

  private val serializer = new BinaryThriftStructSerializer[thriftscala.Span] {
    def codec = thriftscala.Span
  }

  private def encodeTraceId(traceId: Long) = copiedBuffer("full_span:" + traceId, UTF_8)

  override def close() = client.release()

  def storeSpan(span: Span): Future[Unit] = {
    val redisKey = encodeTraceId(span.traceId)
    val thrift = new ThriftSpan(span).toThrift
    val buf = ChannelBuffers.copiedBuffer(serializer.toBytes(thrift))
    client.lPush(redisKey, List(buf)).flatMap(_ => expireOnTtl(redisKey))
  }

  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] =
    expireOnTtl(encodeTraceId(traceId), Some(ttl))

  def getTimeToLive(traceId: Long): Future[Duration] =
    client.ttl(encodeTraceId(traceId)) map { ttl =>
      if (ttl.isDefined) Duration.fromSeconds(ttl.get.intValue()) else Duration.Top
    }

  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    client.lRange(encodeTraceId(traceId), 0L, -1L) map
      (_.map(decodeSpan).sortBy(timestampOfFirstAnnotation)(Ordering.Long.reverse))

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] =
    Future.collect(traceIds.map(getSpansByTraceId))
      .map(_.filter(spans => spans.size > 0)) // prune empties

  def getDataTimeToLive: Int = (defaultTtl map (_.inSeconds)).getOrElse(Int.MaxValue)

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] =
    Future.collect(traceIds map { id =>
      // map the exists result to the trace id or none
      client.exists(encodeTraceId(id)) map (exists => if (exists) Some(id) else None)
    }).map(_.flatten.toSet)

  private def decodeSpan(buf: ChannelBuffer): Span = {
    val thrift = serializer.fromBytes(buf.copy().array)
    new WrappedSpan(thrift).toSpan
  }

  private def timestampOfFirstAnnotation(span: Span) =
    span.firstAnnotation.map(a => a.timestamp).getOrElse(0L)
}
