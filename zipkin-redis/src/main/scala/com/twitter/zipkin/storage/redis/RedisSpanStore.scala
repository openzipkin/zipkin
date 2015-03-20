package com.twitter.zipkin.storage.redis

import com.twitter.zipkin.storage._

import com.twitter.util.{Time, Future, Duration}
import java.nio.ByteBuffer
import com.twitter.conversions.time._
import com.twitter.finagle.{Filter => FFilter, Service}
import com.twitter.util.{Closable, CloseAwaitably, Duration, Future, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span
import java.nio.ByteBuffer
import scala.collection.mutable

import com.twitter.util.{Closable, CloseAwaitably, Duration, Future, Time}

/**
 * Created by caporp01 on 19/03/2015.
 */
class RedisSpanStore ( index : RedisIndex, storage : RedisStorage ) extends SpanStore {

  private[this] def call[T](f: => T): Future[T] = synchronized { Future(f) }

  def close(deadline: Time): Future[Unit] = closeAwaitably {
      call { storage.close() }.unit
    }

    def apply(newSpans: Seq[Span]): Future[Unit] = call {
      newSpans foreach {
        span =>
          storage.storeSpan(span)
          index.indexServiceName(span)
          index.indexSpanNameByService(span)
          index.indexTraceIdByServiceAndName(span)
          index.indexSpanByAnnotations(span)
          index.indexSpanDuration(span)
      }
    }.unit

    // Used for pinning
    def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
      storage.setTimeToLive(traceId, ttl)
    }

    def getTimeToLive(traceId: Long): Future[Duration] = {
      storage.getTimeToLive(traceId)
    }

    def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
      storage.tracesExist(traceIds)
    }

    def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
      storage.getSpansByTraceIds(traceIds)
    }

    def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = {
      storage.getSpansByTraceId(traceId)
    }

    def getTraceIdsByName(
                           serviceName: String,
                           spanName: Option[String],
                           endTs: Long,
                           limit: Int
                           ): Future[Seq[IndexedTraceId]] = {
      index.getTraceIdsByName(serviceName, spanName, endTs, limit)
    }

    def getTraceIdsByAnnotation(serviceName: String,
                                 annotation: String,
                                 value: Option[ByteBuffer],
                                 endTs: Long,
                                 limit: Int
                                 ): Future[Seq[IndexedTraceId]] = {
      index.getTraceIdsByAnnotation(serviceName, annotation, value, endTs, limit)
    }

    def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = index.getTracesDuration(traceIds)

    def getAllServiceNames: Future[Set[String]] = {
      println("Getting service names")
      index.getServiceNames
    }

    def getSpanNames(serviceName: String): Future[Set[String]] = index.getSpanNames(serviceName)
}
