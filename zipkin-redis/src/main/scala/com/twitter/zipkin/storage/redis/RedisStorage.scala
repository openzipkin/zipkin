package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets._
import com.twitter.finagle.redis.Client
import com.twitter.scrooge.{CompactThriftSerializer, ThriftStructSerializer}
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift.{WrappedSpan, ThriftSpan}
import com.twitter.zipkin.thriftscala
import java.io.Closeable
import org.jboss.netty.buffer.ChannelBuffers._
import org.jboss.netty.buffer.ChannelBuffer

/**
 * @param client the redis client to use
 * @param ttl expires keys older than this many seconds.
 * @param serializer the serializer to be used to convert the span to a byte representation
 */
class RedisStorage(
  val client: Client,
  val ttl: Option[Duration],
  val serializer: ThriftStructSerializer[thriftscala.Span] = new CompactThriftSerializer[thriftscala.Span] {
    override def codec = thriftscala.Span
  }
) extends Closeable with ExpirationSupport {


  var snappyCodec = new RedisSnappyThriftCodec[thriftscala.Span](serializer)
  private def encodeTraceId(traceId: Long) = copiedBuffer("full_span:" + traceId, UTF_8)

  override def close() = client.release()

  def storeSpan(span: Span): Future[Unit] = {
    val redisKey = encodeTraceId(span.traceId)
    val thrift = new ThriftSpan(span).toThrift
    val buf = snappyCodec.encode(thrift)
    client.lPush(redisKey, List(buf)).flatMap(_ => expireOnTtl(redisKey))
  }

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] =
    Future.collect(traceIds.map(getSpansByTraceId))
      .map(_.filter(spans => spans.size > 0)) // prune empties

  private[this] def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    client.lRange(encodeTraceId(traceId), 0L, -1L) map
      (_.map(decodeSpan).sortBy(timestampOfFirstAnnotation)(Ordering.Long.reverse))

  private def decodeSpan(buf: ChannelBuffer): Span = {
    new WrappedSpan(snappyCodec.decode(buf)).toSpan
  }

  private def timestampOfFirstAnnotation(span: Span) =
    span.firstAnnotation.map(a => a.timestamp).getOrElse(0L)
}