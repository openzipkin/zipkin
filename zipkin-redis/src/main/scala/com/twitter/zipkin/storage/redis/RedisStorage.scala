package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets._
import com.twitter.finagle.redis.Client
import com.twitter.scrooge.{CompactThriftSerializer, ThriftStructSerializer}
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.{Trace, Span}
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

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[List[Span]]] =
    Future.collect(traceIds.map(getSpansByTraceId))
      .map(_.filterNot(_.isEmpty)) // prune empties
      .map(_.map(Trace(_).spans)) // merge by span id
      .map(_.sortBy(_.head)) // sort traces by the first span

  private[this] def getSpansByTraceId(traceId: Long): Future[List[Span]] =
    client.lRange(encodeTraceId(traceId), 0L, -1L) map
      (_.map(decodeSpan).sorted)

  private def decodeSpan(buf: ChannelBuffer): Span = {
    new WrappedSpan(snappyCodec.decode(buf)).toSpan
  }
}