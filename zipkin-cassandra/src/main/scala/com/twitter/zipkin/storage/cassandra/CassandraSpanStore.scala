/*
 * Copyright 2012 Twitter Inc.
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
package com.twitter.zipkin.storage.cassandra

import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util.{Future, Duration}
import com.twitter.zipkin.adjuster.{ApplyTimestampAndDuration, CorrectForClockSkew, MergeById}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.zipkin.storage.{CollectAnnotationQueries, IndexedTraceId, SpanStore}
import com.twitter.zipkin.util.FutureUtil
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import org.twitter.zipkin.storage.cassandra.Repository
import scala.collection.JavaConverters._

object CassandraSpanStoreDefaults {
  val KeyspaceName = Repository.KEYSPACE
  val SpanTtl = 7.days
  val IndexTtl = 3.days
  val MaxTraceCols = 100000
  val SpanCodec = new ScroogeThriftCodec[ThriftSpan](ThriftSpan)
}

abstract class CassandraSpanStore(
  stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore"),
  spanTtl: Duration = CassandraSpanStoreDefaults.SpanTtl,
  indexTtl: Duration = CassandraSpanStoreDefaults.IndexTtl,
  maxTraceCols: Int = CassandraSpanStoreDefaults.MaxTraceCols
) extends SpanStore with CollectAnnotationQueries {

  /** Deferred as repository eagerly creates network connections */
  protected def repository: Repository

  private[this] val IndexDelimiter = ":"
  private[this] val IndexDelimiterBytes = IndexDelimiter.getBytes
  private[this] val spanCodec = CassandraSpanStoreDefaults.SpanCodec

  /**
   * Internal helper methods
   */
  private[this] def createSpanColumnName(span: Span): String =
    "%d_%d_%d".format(span.id, span.annotations.hashCode, span.binaryAnnotations.hashCode)

  private[this] def annotationKey(serviceName: String, annotation: String, value: Option[ByteBuffer]): ByteBuffer = {
    ByteBuffer.wrap(
      serviceName.getBytes ++ IndexDelimiterBytes ++ annotation.getBytes ++
      value.map { v => IndexDelimiterBytes ++ Util.getArrayFromBuffer(v) }.getOrElse(Array()))
  }

  /**
   * Stats
   */
  private[this] val SpansStats = stats.scope("spans")
  private[this] val SpansStoredCounter = SpansStats.counter("stored")
  private[this] val SpansIndexedCounter = SpansStats.counter("indexed")
  private[this] val IndexStats = stats.scope("index")
  private[this] val IndexServiceNameCounter = IndexStats.counter("serviceName")
  private[this] val IndexServiceNameNoNameCounter = IndexStats.scope("serviceName").counter("noName")
  private[this] val IndexSpanNameCounter = IndexStats.scope("serviceName").counter("spanName")
  private[this] val IndexSpanNameNoNameCounter = IndexStats.scope("serviceName").scope("spanName").counter("noName")
  private[this] val IndexTraceStats = IndexStats.scope("trace")
  private[this] val IndexTraceNoTimestampCounter = IndexTraceStats.counter("noTimestamp")
  private[this] val IndexTraceByServiceNameCounter = IndexTraceStats.counter("serviceName")
  private[this] val IndexTraceBySpanNameCounter = IndexTraceStats.counter("spanName")
  private[this] val IndexAnnotationCounter = IndexStats.scope("annotation").counter("standard")
  private[this] val IndexBinaryAnnotationCounter = IndexStats.scope("annotation").counter("binary")
  private[this] val IndexSpanNoTimestampCounter = IndexStats.scope("span").counter("noTimestamp")
  private[this] val QueryStats = stats.scope("query")
  private[this] val QueryGetSpansByTraceIdsStat = QueryStats.stat("getSpansByTraceIds")
  private[this] val QueryGetSpansByTraceIdsTooBigCounter = QueryStats.scope("getSpansByTraceIds").counter("tooBig")
  private[this] val QueryGetServiceNamesCounter = QueryStats.counter("getServiceNames")
  private[this] val QueryGetSpanNamesCounter = QueryStats.counter("getSpanNames")
  private[this] val QueryGetTraceIdsByNameCounter = QueryStats.counter("getTraceIdsByName")
  private[this] val QueryGetTraceIdsByAnnotationCounter = QueryStats.counter("getTraceIdsByAnnotation")

  /**
   * Internal indexing helpers
   */
  private[this] def indexServiceName(span: Span): Future[Unit] = {
    IndexServiceNameCounter.incr()
    Future.join(span.serviceNames.toList map {
      case "" =>
        IndexServiceNameNoNameCounter.incr()
        Future.value(())
      case s =>
        FutureUtil.toFuture(repository.storeServiceName(s, indexTtl.inSeconds))
    })
  }

  private[this] def indexSpanNameByService(span: Span): Future[Unit] = {
    if (span.name == "") {
      IndexSpanNameNoNameCounter.incr()
      Future.value(())
    } else {
      IndexSpanNameCounter.incr()

      Future.join(
        span.serviceNames.toSeq map { serviceName =>
          FutureUtil.toFuture(repository.storeSpanName(serviceName, span.name, indexTtl.inSeconds))
        })
    }
  }

  private[this] def indexTraceIdByName(span: Span): Future[Unit] = {
    if (span.timestamp.isEmpty)
      IndexTraceNoTimestampCounter.incr()

    span.timestamp map { timestamp =>
      val serviceNames = span.serviceNames

      Future.join(
        serviceNames.toList map { serviceName =>
          IndexTraceByServiceNameCounter.incr()
          val storeFuture =
            FutureUtil.toFuture(repository.storeTraceIdByServiceName(serviceName, timestamp, span.traceId, indexTtl.inSeconds))

          if (span.name != "") {
            IndexTraceBySpanNameCounter.incr()

            Future.join(
              storeFuture,
              FutureUtil.toFuture(repository.storeTraceIdBySpanName(serviceName, span.name, timestamp, span.traceId, indexTtl.inSeconds)))
          } else storeFuture
        })
    } getOrElse Future.value(())
  }

  private[this] def indexByAnnotations(span: Span): Future[Unit] = {
    if (span.timestamp.isEmpty)
      IndexSpanNoTimestampCounter.incr()

    span.timestamp map { timestamp =>

      val annotationsFuture = Future.join(
        span.annotations
          .groupBy(_.value)
          .flatMap { case (_, as) =>
            val a = as.min
            a.host map { endpoint =>
              IndexAnnotationCounter.incr()

              FutureUtil.toFuture(
                repository.storeTraceIdByAnnotation(
                  annotationKey(endpoint.serviceName, a.value, None), timestamp, span.traceId, indexTtl.inSeconds))
            }
          }.toList)

      val binaryFuture = Future.join(span.binaryAnnotations flatMap { ba =>
        ba.host map { endpoint =>
          IndexBinaryAnnotationCounter.incr()

          Future.join(
            FutureUtil.toFuture(
              repository.storeTraceIdByAnnotation(
                annotationKey(endpoint.serviceName, ba.key, Some(ba.value)), timestamp, span.traceId, indexTtl.inSeconds)),
            FutureUtil.toFuture(
              repository.storeTraceIdByAnnotation(
                annotationKey(endpoint.serviceName, ba.key, None), timestamp, span.traceId, indexTtl.inSeconds)))
        }
      })

      Future.join(annotationsFuture, binaryFuture).map(_ => ())
    } getOrElse Future.value(())
  }

  private[this] def getSpansByTraceIds(traceIds: Seq[Long], count: Int): Future[Seq[List[Span]]] = {
    FutureUtil.toFuture(repository.getSpansByTraceIds(traceIds.toArray.map(Long.box), count))
      .map { spansByTraceId =>
        val spans =
          spansByTraceId.asScala.mapValues { spans => spans.asScala.map(spanCodec.decode(_).toSpan) }

        traceIds.flatMap(traceId => spans.get(traceId))
          .map(MergeById)
          .map(CorrectForClockSkew)
          .map(ApplyTimestampAndDuration)
          .sortBy(_.head) // CQL doesn't allow order by with an "in" query
      }
  }

  /**
   * API Implementation
   */
  override def close() = repository.close()

  override def apply(spans: Seq[Span]): Future[Unit] = {
    SpansStoredCounter.incr(spans.size)

    Future.join(
      spans.map(s => s.copy(annotations = s.annotations.sorted))
           .map(ApplyTimestampAndDuration.apply).map { span =>
        SpansIndexedCounter.incr()

        Future.join(
          FutureUtil.toFuture(
            repository.storeSpan(
              span.traceId,
              span.timestamp.getOrElse(0L),
              createSpanColumnName(span),
              spanCodec.encode(span.toThrift),
              spanTtl.inSeconds)),
          indexServiceName(span),
          indexSpanNameByService(span),
          indexTraceIdByName(span),
          indexByAnnotations(span))
      })
  }

  override def getTracesByIds(traceIds: Seq[Long]): Future[Seq[List[Span]]] = {
    QueryGetSpansByTraceIdsStat.add(traceIds.size)
    getSpansByTraceIds(traceIds, maxTraceCols)
  }

  override def getAllServiceNames(): Future[Seq[String]] = {
    QueryGetServiceNamesCounter.incr()
    FutureUtil.toFuture(repository.getServiceNames).map(_.asScala.toList.sorted)
  }

  override def getSpanNames(service: String): Future[Seq[String]] = {
    QueryGetSpanNamesCounter.incr()
    FutureUtil.toFuture(repository.getSpanNames(service)).map(_.asScala.toList.sorted)
  }

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    lookback: Long, // TODO
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByNameCounter.incr()

    val traceIdsFuture = FutureUtil.toFuture(spanName match {
      // if we have a span name, look up in the service + span name index
      // if not, look up by service name only
      case Some(x :String) => repository.getTraceIdsBySpanName(serviceName, x, endTs, limit)
      case None => repository.getTraceIdsByServiceName(serviceName, endTs, limit)
    })

    traceIdsFuture.map { traceIds =>
      traceIds.asScala
        .map { case (traceId, ts) => IndexedTraceId(traceId, timestamp = ts) }
        .toSeq
    }
  }

  override def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    lookback: Long, // TODO
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByAnnotationCounter.incr()

    FutureUtil.toFuture(
      repository
        .getTraceIdsByAnnotation(annotationKey(serviceName, annotation, value), endTs, limit))
      .map { traceIds =>
        traceIds.asScala
          .map { case (traceId, ts) => IndexedTraceId(traceId, timestamp = ts) }
          .toSeq
      }
  }
}
