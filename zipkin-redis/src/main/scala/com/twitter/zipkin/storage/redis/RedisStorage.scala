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
 * @param ttl expires keys older than this many seconds.
 */
class RedisStorage(
  val client: Client,
  val ttl: Option[Duration]
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

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] =
    Future.collect(traceIds.map(getSpansByTraceId))
      .map(_.filter(spans => spans.size > 0)) // prune empties

  private[this] def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    client.lRange(encodeTraceId(traceId), 0L, -1L) map
      (_.map(decodeSpan).sortBy(timestampOfFirstAnnotation)(Ordering.Long.reverse))

  private def decodeSpan(buf: ChannelBuffer): Span = {
    val thrift = serializer.fromBytes(buf.copy().array)
    new WrappedSpan(thrift).toSpan
  }

  private def timestampOfFirstAnnotation(span: Span) =
    span.firstAnnotation.map(a => a.timestamp).getOrElse(0L)
}
