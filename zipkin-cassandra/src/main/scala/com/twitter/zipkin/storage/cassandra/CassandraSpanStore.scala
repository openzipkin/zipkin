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
import com.twitter.util.{Future, FuturePool, Duration}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.zipkin.storage.{IndexedTraceId, SpanStore}
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import org.twitter.zipkin.storage.cassandra.Repository
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

object CassandraSpanStoreDefaults {
  val KeyspaceName = Repository.KEYSPACE
  val SpanTtl = 7.days
  val IndexTtl = 3.days
  val MaxTraceCols = 100000
  val SpanCodec = new ScroogeThriftCodec[ThriftSpan](ThriftSpan)
}

class CassandraSpanStore(
  repository: Repository,
  stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore"),
  spanTtl: Duration = CassandraSpanStoreDefaults.SpanTtl,
  indexTtl: Duration = CassandraSpanStoreDefaults.IndexTtl,
  maxTraceCols: Int = CassandraSpanStoreDefaults.MaxTraceCols
) extends SpanStore {
  private[this] val IndexDelimiter = ":"
  private[this] val IndexDelimiterBytes = IndexDelimiter.getBytes
  private[this] val spanCodec = CassandraSpanStoreDefaults.SpanCodec

  /**
   * Internal helper methods
   */
  private[this] def createSpanColumnName(span: Span): String =
    "%d_%d_%d".format(span.id, span.annotations.hashCode, span.binaryAnnotations.hashCode)

  private[this] def nameKey(serviceName: String, spanName: Option[String]): String =
    (serviceName + spanName.map("." + _).getOrElse("")).toLowerCase

  private[this] def annotationKey(serviceName: String, annotation: String, value: Option[ByteBuffer]): ByteBuffer = {
    ByteBuffer.wrap(
      serviceName.getBytes ++ IndexDelimiterBytes ++ annotation.getBytes ++
      value.map { v => IndexDelimiterBytes ++ Util.getArrayFromBuffer(v) }.getOrElse(Array()))
  }

  private[this] val pool = FuturePool.unboundedPool

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
  private[this] val IndexTraceNoLastAnnotationCounter = IndexTraceStats.counter("noLastAnnotation")
  private[this] val IndexTraceByServiceNameCounter = IndexTraceStats.counter("serviceName")
  private[this] val IndexTraceBySpanNameCounter = IndexTraceStats.counter("spanName")
  private[this] val IndexAnnotationCounter = IndexStats.scope("annotation").counter("standard")
  private[this] val IndexAnnotationNoLastAnnotationCounter = IndexStats.scope("annotation").counter("noLastAnnotation")
  private[this] val IndexBinaryAnnotationCounter = IndexStats.scope("annotation").counter("binary")
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
  private[this] def indexServiceName(span: Span) {
    IndexServiceNameCounter.incr()
    span.serviceNames foreach {
      case "" =>
        IndexServiceNameNoNameCounter.incr()
      case s =>
        repository.storeServiceName(s.toLowerCase, indexTtl.inSeconds)
    }
  }

  private[this] def indexSpanNameByService(span: Span) {
    if (span.name == "") {
      IndexSpanNameNoNameCounter.incr()
    } else {
      IndexSpanNameCounter.incr()
      span.serviceNames foreach {
        repository.storeSpanName(_, span.name.toLowerCase, indexTtl.inSeconds)
      }
    }
  }

  private[this] def indexTraceIdByName(span: Span) {
    if (span.lastAnnotation.isEmpty)
      IndexTraceNoLastAnnotationCounter.incr()

    span.lastAnnotation foreach { lastAnnotation =>
      val timestamp = lastAnnotation.timestamp
      val serviceNames = span.serviceNames

      serviceNames foreach { serviceName =>

        IndexTraceByServiceNameCounter.incr()
        repository.storeTraceIdByServiceName(serviceName, timestamp, span.traceId, indexTtl.inSeconds)

        if (span.name != "") {
          IndexTraceBySpanNameCounter.incr()
          repository.storeTraceIdBySpanName(serviceName, span.name, timestamp, span.traceId, indexTtl.inSeconds)
        }
      }
    }
  }

  private[this] def indexByAnnotations(span: Span) {
    if (span.lastAnnotation.isEmpty)
      IndexAnnotationNoLastAnnotationCounter.incr()

    span.lastAnnotation foreach { lastAnnotation =>
      val timestamp = lastAnnotation.timestamp

      // skip core annotations since that query can be done by service name/span name anyway
      span.annotations
        .filter { a => !Constants.CoreAnnotations.contains(a.value) }
        .groupBy(_.value)
        .foreach { case (_, as) =>
          val a = as.min
          a.host foreach { endpoint =>
            IndexAnnotationCounter.incr()

            repository.storeTraceIdByAnnotation(
              annotationKey(endpoint.serviceName, a.value, None), timestamp, span.traceId, indexTtl.inSeconds)
          }
        }

      span.binaryAnnotations foreach { ba =>
        ba.host foreach { endpoint =>
          IndexBinaryAnnotationCounter.incr()

          repository.storeTraceIdByAnnotation(
            annotationKey(endpoint.serviceName, ba.key, Some(ba.value)), timestamp, span.traceId, indexTtl.inSeconds)

          repository.storeTraceIdByAnnotation(
            annotationKey(endpoint.serviceName, ba.key, None), timestamp, span.traceId, indexTtl.inSeconds)
        }
      }
    }
  }

  private[this] def getSpansByTraceIds(traceIds: Seq[Long], count: Int): Future[Seq[Seq[Span]]] = {
    pool {
      val spans = repository.getSpansByTraceIds(traceIds.toArray.map(Long.box), count)
        .mapValues { case spans :java.util.List[ByteBuffer] => spans.asScala.map(spanCodec.decode(_).toSpan) }

      traceIds.map(traceId => spans.get(traceId)).flatten
    }
  }

  /**
   * API Implementation
   */
  override def close() = repository.close()

  override def apply(spans: Seq[Span]): Future[Unit] = {
    SpansStoredCounter.incr(spans.size)

    spans foreach { span =>
      repository.storeSpan(
        span.traceId,
        span.lastTimestamp.getOrElse(span.firstTimestamp.getOrElse(0)),
        createSpanColumnName(span),
        spanCodec.encode(span.toThrift),
        spanTtl.inSeconds)

      SpansIndexedCounter.incr()
      indexServiceName(span)
      indexSpanNameByService(span)
      indexTraceIdByName(span)
      indexByAnnotations(span)
    }

    Future.Unit
  }

  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    QueryGetSpansByTraceIdsStat.add(traceIds.size)
    getSpansByTraceIds(traceIds, maxTraceCols)
  }

  override def getAllServiceNames: Future[Set[String]] = {
    QueryGetServiceNamesCounter.incr()
    pool { repository.getServiceNames.asScala.toSet }
  }

  override def getSpanNames(service: String): Future[Set[String]] = {
    QueryGetSpanNamesCounter.incr()
    pool { repository.getSpanNames(service).asScala.toSet }
  }

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByNameCounter.incr()

    pool {
      (spanName match {
        // if we have a span name, look up in the service + span name index
        // if not, look up by service name only
        case Some(x :String) => repository.getTraceIdsBySpanName(serviceName, x, endTs, limit)
        case None => repository.getTraceIdsByServiceName(serviceName, endTs, limit)
      })
      .map { case (traceId : java.lang.Long, ts :java.lang.Long) => IndexedTraceId(traceId, timestamp = ts) }
      .toSeq
    }
  }

  override def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByAnnotationCounter.incr()

    pool {
      repository
        .getTraceIdsByAnnotation(annotationKey(serviceName, annotation, value), endTs, limit)
        .map { case (traceId :java.lang.Long, ts :java.lang.Long) => IndexedTraceId(traceId, timestamp = ts) }
        .toSeq
    }
  }
}
