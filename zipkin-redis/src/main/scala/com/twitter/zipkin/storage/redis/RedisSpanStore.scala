package com.twitter.zipkin.storage.redis

import java.nio.ByteBuffer

import com.google.common.io.Closer
import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage._

/**
 * @param client the redis client to use
 * @param ttl expires keys older than this many seconds.
 */
class RedisSpanStore(client: Client, ttl: Option[Duration]) extends SpanStore {
  private[this] val closer = Closer.create()
  private[this] val index = closer.register(new RedisIndex(client, ttl))
  private[this] val storage = closer.register(new RedisStorage(client, ttl))

  /** For testing, clear this store. */
  private[redis] def clear(): Future[Unit] = client.flushDB()

  override def close() = closer.close()

  override def apply(newSpans: Seq[Span]): Future[Unit] = Future.join(newSpans.flatMap { span =>
    Seq(storage.storeSpan(span), index.index(span))
  })

  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    storage.getSpansByTraceIds(traceIds)
  }

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    index.getTraceIdsByName(serviceName, spanName, endTs, limit)
  }

  override def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    index.getTraceIdsByAnnotation(serviceName, annotation, value, endTs, limit)
  }

  override def getAllServiceNames: Future[Set[String]] = index.getServiceNames

  override def getSpanNames(serviceName: String): Future[Set[String]] = index.getSpanNames(serviceName)
}
