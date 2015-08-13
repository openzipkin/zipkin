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
import com.twitter.util.{Future, FuturePool, Duration, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.zipkin.storage.{TraceIdDuration, IndexedTraceId, SpanStore}
import com.twitter.zipkin.util.Util
import java.nio.ByteBuffer
import org.twitter.zipkin.storage.cassandra.Repository
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

case class ZipkinColumnFamilyNames(
  traces: String = "traces",
  serviceNames: String = "service_names",
  spanNames: String = "span_names",
  serviceNameIndex: String = "service_name_index",
  serviceSpanNameIndex: String = "service_span_name_index",
  annotationsIndex: String = "annotations_index",
  durationIndex: String = "duration_index")

object CassandraSpanStoreDefaults {
  val KeyspaceName = "zipkin"
  val ColumnFamilyNames = ZipkinColumnFamilyNames()
  val SpanTtl = 7.days
  val IndexTtl = 3.days
  val IndexBuckets = 10
  val MaxTraceCols = 100000
  val ReadBatchSize = 500
  val SpanCodec = new SnappyCodec(new ScroogeThriftCodec[ThriftSpan](ThriftSpan))
}

class CassandraSpanStore(
  repository: Repository,
  stats: StatsReceiver = DefaultStatsReceiver.scope("CassandraSpanStore"),
  cfs: ZipkinColumnFamilyNames = CassandraSpanStoreDefaults.ColumnFamilyNames,
  spanTtl: Duration = CassandraSpanStoreDefaults.SpanTtl,
  indexTtl: Duration = CassandraSpanStoreDefaults.IndexTtl,
  bucketsCount: Int = CassandraSpanStoreDefaults.IndexBuckets,
  maxTraceCols: Int = CassandraSpanStoreDefaults.MaxTraceCols,
  readBatchSize: Int = CassandraSpanStoreDefaults.ReadBatchSize,
  spanCodec: Codec[ThriftSpan] = CassandraSpanStoreDefaults.SpanCodec
) extends SpanStore {
  private[this] val ServiceNamesKey = "servicenames"
  private[this] val IndexDelimiter = ":"
  private[this] val IndexDelimiterBytes = IndexDelimiter.getBytes
  private[this] val SomeIndexTtl = Some(indexTtl)

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
  private[this] val IndexDurationCounter = IndexStats.counter("duration")
  private[this] val QueryStats = stats.scope("query")
  private[this] val QueryGetTtlCounter = QueryStats.counter("getTimeToLive")
  private[this] val QueryTracesExistStat = QueryStats.stat("tracesExist")
  private[this] val QueryGetSpansByTraceIdsStat = QueryStats.stat("getSpansByTraceIds")
  private[this] val QueryGetSpansByTraceIdsTooBigCounter = QueryStats.scope("getSpansByTraceIds").counter("tooBig")
  private[this] val QueryGetServiceNamesCounter = QueryStats.counter("getServiceNames")
  private[this] val QueryGetSpanNamesCounter = QueryStats.counter("getSpanNames")
  private[this] val QueryGetTraceIdsByNameCounter = QueryStats.counter("getTraceIdsByName")
  private[this] val QueryGetTraceIdsByAnnotationCounter = QueryStats.counter("getTraceIdsByAnnotation")
  private[this] val QueryGetTracesDurationStat = QueryStats.stat("getTracesDuration")

  /**
   * Internal indexing helpers
   */
  private[this] def indexServiceName(span: Span) {
    IndexServiceNameCounter.incr()
    span.serviceNames foreach {
      case "" =>
        IndexServiceNameNoNameCounter.incr()
      case s =>
        // @xxx so many identical writes going to the one partition key is bad. implement caching of writes.
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

  private[this] def indexSpanDuration(span: Span) {
    Seq(span.firstAnnotation, span.lastAnnotation).flatten foreach { a =>
      IndexDurationCounter.incr()
      repository.storeTraceDuration(span.traceId, a.timestamp, indexTtl.inSeconds)
    }
  }

  private[this] def getSpansByTraceIds(traceIds: Seq[Long], count: Int): Future[Seq[Seq[Span]]] = {

    val results = traceIds.grouped(readBatchSize) map { idBatch =>
      pool {
        val spans = repository.getSpansByTraceIds(idBatch.toArray.map(Long.box), count)
          .mapValues { case spans :java.util.List[ByteBuffer] => spans.asScala.map(spanCodec.decode(_).toSpan) }

        traceIds.map(traceId => spans.get(traceId)).flatten.toSeq
      }
    }

    Future.collect(results.toSeq).map(_.flatten)
  }

  /**
   * API Implementation
   */
  override def close(deadline: Time): Future[Unit] =
    FuturePool.unboundedPool { repository.close() }

  def apply(spans: Seq[Span]): Future[Unit] = {
    SpansStoredCounter.incr(spans.size)

    spans foreach { span =>
      repository.storeSpan(span.traceId, createSpanColumnName(span), spanCodec.encode(span.toThrift), spanTtl.inSeconds)
      if (shouldIndex(span)) {
        SpansIndexedCounter.incr()
        indexServiceName(span)
        indexSpanNameByService(span)
        indexTraceIdByName(span)
        indexByAnnotations(span)
        indexSpanDuration(span)
      }
    }

    Future.Unit
  }

  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
    getSpansByTraceId(traceId).get foreach { span =>
      repository.storeSpan(traceId, createSpanColumnName(span), spanCodec.encode(span.toThrift), ttl.inSeconds)
    }
    Future.Unit
  }

  def getTimeToLive(traceId: Long): Future[Duration] = {
    QueryGetTtlCounter.incr()

    pool {
      Duration(repository.getSpanTtlSeconds(traceId), java.util.concurrent.TimeUnit.SECONDS)
    }
  }

  override def getDataTimeToLive = Future.value(spanTtl.inSeconds)

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    QueryTracesExistStat.add(traceIds.size)
    pool {
      repository
        .tracesExist(traceIds.toArray.map(Long.box))
        .map(_.asInstanceOf[Long])
        .toSet
    }
  }

  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] =
    getSpansByTraceIds(Seq(traceId)).map(_.head)

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    QueryGetSpansByTraceIdsStat.add(traceIds.size)
    getSpansByTraceIds(traceIds, maxTraceCols)
  }

  def getAllServiceNames: Future[Set[String]] = {
    QueryGetServiceNamesCounter.incr()
    pool { repository.getServiceNames.asScala.toSet }
  }

  def getSpanNames(service: String): Future[Set[String]] = {
    QueryGetSpanNamesCounter.incr()
    pool { repository.getSpanNames(service).asScala.toSet }
  }

  def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByNameCounter.incr()
    val key = nameKey(serviceName, spanName)

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

  def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByAnnotationCounter.incr()
    val key = annotationKey(serviceName, annotation, value)

    pool {
      repository
        .getTraceIdsByAnnotation(annotationKey(serviceName, annotation, value), endTs, limit)
        .map { case (traceId :java.lang.Long, ts :java.lang.Long) => IndexedTraceId(traceId, timestamp = ts) }
        .toSeq
    }
  }

  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
    QueryGetTracesDurationStat.add(traceIds.size)

    val traceIdSet = traceIds.toArray

    pool {
      val durations = (repository.getTraceDuration(true, traceIdSet)
      .map { case (traceId :java.lang.Long, ts :java.lang.Long) =>
        (traceId.asInstanceOf[Long], ("s", ts.asInstanceOf[Long]))}
      .toSeq ++
        (repository.getTraceDuration(false, traceIdSet)
        .map { case (traceId :java.lang.Long, ts :java.lang.Long) =>
          (traceId.asInstanceOf[Long], ("e", ts.asInstanceOf[Long]))}
        .toSeq))
      .groupBy { case (traceId, _) => traceId }
      .mapValues(_.map(_._2))
      .map { case (traceId :Long, Seq(("s", startTs :Long),("e", endTs :Long))) =>
        (traceId, TraceIdDuration(traceId, endTs - startTs, startTs))
      }

      traceIds.map(traceId => durations.get(traceId.toInt)).flatten.toSeq
    }
  }
}
