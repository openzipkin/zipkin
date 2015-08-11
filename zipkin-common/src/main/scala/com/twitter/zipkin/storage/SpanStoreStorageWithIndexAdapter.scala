package com.twitter.zipkin.storage

import com.twitter.util.{Future, Await, Duration}
import com.twitter.zipkin.common.Span
import java.nio.ByteBuffer

/**
 * Bridges [[SpanStore]] to the legacy [[Storage]] and [[Index]] traits.
 *
 * Methods that index are no-op as [[SpanStore.apply()]] already indexes.
 */
class SpanStoreStorageWithIndexAdapter(delegate: SpanStore) extends Storage with Index  {

  override def storeSpan(span: Span) = delegate.apply(Seq(span))

  override def getTimeToLive(traceId: Long) = delegate.getTimeToLive(traceId)

  override def getSpansByTraceId(traceId: Long) = delegate.getSpansByTraceId(traceId)

  override def setTimeToLive(traceId: Long, ttl: Duration) = delegate.setTimeToLive(traceId, ttl)

  override def tracesExist(traceIds: Seq[Long]) = delegate.tracesExist(traceIds)

  override def getSpansByTraceIds(traceIds: Seq[Long]) = delegate.getSpansByTraceIds(traceIds)

  override def getDataTimeToLive: Int = Await.result(delegate.getDataTimeToLive())

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int) = delegate.getTraceIdsByName(serviceName, spanName, endTs, limit)

  override def getServiceNames = delegate.getAllServiceNames

  override def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int) = delegate.getTraceIdsByAnnotation(serviceName, annotation, value, endTs, limit)

  override def getSpanNames(service: String) = delegate.getSpanNames(service)

  override def getTracesDuration(traceIds: Seq[Long]) = delegate.getTracesDuration(traceIds)

  override def close() = Await.ready(delegate.close())

  // Indexing is implied by SpanStore.apply
  override def indexServiceName(span: Span) = Future.Unit

  override def indexSpanNameByService(span: Span) = Future.Unit

  override def indexTraceIdByServiceAndName(span: Span) = Future.Unit

  override def indexSpanByAnnotations(span: Span) = Future.Unit

  override def indexSpanDuration(span: Span) = Future.Unit
}
