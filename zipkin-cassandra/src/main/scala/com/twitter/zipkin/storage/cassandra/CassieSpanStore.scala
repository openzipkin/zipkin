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

import com.twitter.cassie
import com.twitter.cassie._
import com.twitter.cassie.codecs.{Codec, LongCodec, Utf8Codec}
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
import scala.collection.JavaConverters._

case class ZipkinColumnFamilyNames(
  traces: String = "Traces",
  serviceNames: String = "ServiceNames",
  spanNames: String = "SpanNames",
  serviceNameIndex: String = "ServiceNameIndex",
  serviceSpanNameIndex: String = "ServiceSpanNameIndex",
  annotationsIndex: String = "AnnotationsIndex",
  durationIndex: String = "DurationIndex")

object CassieSpanStoreDefaults {
  val KeyspaceName = "Zipkin"
  val ColumnFamilyNames = ZipkinColumnFamilyNames()
  val WriteConsistency = cassie.WriteConsistency.One
  val ReadConsistency = cassie.ReadConsistency.One
  val SpanTtl = 7.days
  val IndexTtl = 3.days
  val IndexBuckets = 10
  val MaxTraceCols = 100000
  val ReadBatchSize = 500
  val SpanCodec = new SnappyCodec(new ScroogeThriftCodec[ThriftSpan](ThriftSpan))
}

class CassieSpanStore(
  keyspace: Keyspace,
  stats: StatsReceiver = DefaultStatsReceiver.scope("CassieSpanStore"),
  cfs: ZipkinColumnFamilyNames = CassieSpanStoreDefaults.ColumnFamilyNames,
  writeConsistency: WriteConsistency = CassieSpanStoreDefaults.WriteConsistency,
  readConsistency: ReadConsistency = CassieSpanStoreDefaults.ReadConsistency,
  spanTtl: Duration = CassieSpanStoreDefaults.SpanTtl,
  indexTtl: Duration = CassieSpanStoreDefaults.IndexTtl,
  bucketsCount: Int = CassieSpanStoreDefaults.IndexBuckets,
  maxTraceCols: Int = CassieSpanStoreDefaults.MaxTraceCols,
  readBatchSize: Int = CassieSpanStoreDefaults.ReadBatchSize,
  spanCodec: Codec[ThriftSpan] = CassieSpanStoreDefaults.SpanCodec
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

  private[this] def newBCF[N, V](cf: String, nCodec: Codec[N], vCodec: Codec[V]) =
    BucketedColumnFamily(keyspace, cf, nCodec, vCodec, writeConsistency, readConsistency)

  private[this] def idxCol[Name, Value](n: Name, v: Value): Column[Name, Value] =
    Column[Name, Value](n, v, None, SomeIndexTtl)

  private[this] def nameKey(serviceName: String, spanName: Option[String]): String =
    (serviceName + spanName.map("." + _).getOrElse("")).toLowerCase

  private[this] def annotationKey(serviceName: String, annotation: String, value: Option[ByteBuffer]): ByteBuffer = {
    ByteBuffer.wrap(
      serviceName.getBytes ++ IndexDelimiterBytes ++ annotation.getBytes ++
      value.map { v => IndexDelimiterBytes ++ Util.getArrayFromBuffer(v) }.getOrElse(Array()))
  }

  private[this] def colToIndexedTraceId(cols: Seq[Column[Long, Long]]): Seq[IndexedTraceId] =
    cols map { c => IndexedTraceId(traceId = c.value, timestamp = c.name) }

  /**
   * Column Families
   * and type aliases to their batch types
   */
  private type BatchTraces = BatchMutationBuilder[Long, String, Span]
  private[this] val Traces = keyspace
    .columnFamily(cfs.traces, LongCodec, Utf8Codec, spanCodec)
    .consistency(writeConsistency)
    .consistency(readConsistency)

  private type BatchServiceNames = BatchMutationBuilder[String, String, String]
  private[this] val ServiceNames = new StringBucketedColumnFamily(
    newBCF(cfs.serviceNames, Utf8Codec, Utf8Codec), bucketsCount)

  private type BatchSpanNames = BatchMutationBuilder[String, String, String]
  private[this] val SpanNames = new StringBucketedColumnFamily(
    newBCF(cfs.spanNames, Utf8Codec, Utf8Codec), bucketsCount)

  private type BatchServiceNameIndex = BatchMutationBuilder[String, Long, Long]
  private[this] val ServiceNameIndex = new StringBucketedColumnFamily(
    newBCF(cfs.serviceNameIndex, LongCodec, LongCodec), bucketsCount)

  private type BatchServiceSpanNameIndex = BatchMutationBuilder[String, Long, Long]
  private[this] val ServiceSpanNameIndex = keyspace
    .columnFamily(cfs.serviceSpanNameIndex, Utf8Codec, LongCodec, LongCodec)
    .consistency(writeConsistency)
    .consistency(readConsistency)

  private type BatchAnnotationsIndex = BatchMutationBuilder[ByteBuffer, Long, Long]
  private[this] val AnnotationsIndex = new ByteBufferBucketedColumnFamily(
    newBCF(cfs.annotationsIndex, LongCodec, LongCodec), bucketsCount)

  private type BatchDurationIndex = BatchMutationBuilder[Long, Long, String]
  private[this] val DurationIndex = keyspace
    .columnFamily(cfs.durationIndex, LongCodec, LongCodec, Utf8Codec)
    .consistency(writeConsistency)
    .consistency(readConsistency)

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
  private[this] def indexServiceName(idx: BatchServiceNames, span: Span) {
    IndexServiceNameCounter.incr()
    span.serviceNames foreach {
      case "" =>
        IndexServiceNameNoNameCounter.incr()
      case s =>
        idx.insert(ServiceNamesKey, idxCol(s.toLowerCase, ""))
    }
  }

  private[this] def indexSpanNameByService(idx: BatchSpanNames, span: Span) {
    if (span.name == "") {
      IndexSpanNameNoNameCounter.incr()
    } else {
      IndexSpanNameCounter.incr()
      val spanNameCol = idxCol(span.name.toLowerCase, "")
      span.serviceNames foreach { idx.insert(_, spanNameCol) }
    }
  }

  private[this] def indexTraceIdByName(
    serviceNameIdx: BatchServiceNameIndex,
    serviceSpanNameIdx: BatchServiceSpanNameIndex,
    span: Span
  ) {
    if (span.lastAnnotation.isEmpty)
      IndexTraceNoLastAnnotationCounter.incr()

    span.lastAnnotation foreach { lastAnnotation =>
      val timestamp = lastAnnotation.timestamp
      val serviceNames = span.serviceNames

      serviceNames foreach { serviceName =>
        val col = idxCol(timestamp, span.traceId)

        IndexTraceByServiceNameCounter.incr()
        serviceNameIdx.insert(nameKey(serviceName, None), col)

        if (span.name != "") {
          IndexTraceBySpanNameCounter.incr()
          serviceSpanNameIdx.insert(nameKey(serviceName, Some(span.name)), col)
        }
      }
    }
  }

  private[this] def indexByAnnotations(idx: BatchAnnotationsIndex, span: Span) {
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
            idx.insert(
              annotationKey(endpoint.serviceName, a.value, None),
              idxCol(a.timestamp, span.traceId))
          }
        }

      span.binaryAnnotations foreach { ba =>
        ba.host foreach { endpoint =>
          IndexBinaryAnnotationCounter.incr()
          val col = idxCol(timestamp, span.traceId)
          idx.insert(annotationKey(endpoint.serviceName, ba.key, Some(ba.value)), col)
          idx.insert(annotationKey(endpoint.serviceName, ba.key, None), col)
        }
      }
    }
  }

  private[this] def indexSpanDuration(idx: BatchDurationIndex, span: Span) {
    Seq(span.firstAnnotation, span.lastAnnotation).flatten foreach { a =>
      IndexDurationCounter.incr()
      idx.insert(span.traceId, idxCol(a.timestamp, ""))
    }
  }

  private[this] def getSpansByTraceIds(traceIds: Seq[Long], count: Int): Future[Seq[Seq[Span]]] = {
    val results = traceIds.grouped(readBatchSize) map { idBatch =>
      Traces.multigetRows(idBatch.toSet.asJava, None, None, Order.Normal, count) map { rowSet =>
        val rows = rowSet.asScala
        idBatch flatMap { id =>
          rows(id).asScala match {
            case cols if cols.isEmpty =>
              None

            case cols if cols.size > maxTraceCols =>
              QueryGetSpansByTraceIdsTooBigCounter.incr()
              None

            case cols =>
              Some(cols.toSeq map { case (_, col) => col.value.toSpan })
          }
        }
      }
    }

    Future.collect(results.toSeq).map(_.flatten)
  }

  /**
   * API Implementation
   */
  override def close(deadline: Time): Future[Unit] =
    FuturePool.unboundedPool { keyspace.close() }

  // TODO: break these into smaller batches?
  def apply(spans: Seq[Span]): Future[Unit] = {
    SpansStoredCounter.incr(spans.size)

    val traces = Traces.batch()
    val serviceNames = ServiceNames.batch()
    val spanNames = SpanNames.batch()
    val serviceNameIdx = ServiceNameIndex.batch()
    val serviceSpanNameIdx = ServiceSpanNameIndex.batch()
    val annotationsIdx = AnnotationsIndex.batch()
    val durationIdx = DurationIndex.batch()

    spans foreach { span =>
      traces.insert(span.traceId, Column[String, ThriftSpan](createSpanColumnName(span), span.toThrift, None, Some(spanTtl)))
      if (shouldIndex(span)) {
        SpansIndexedCounter.incr()
        indexServiceName(serviceNames, span)
        indexSpanNameByService(spanNames, span)
        indexTraceIdByName(serviceNameIdx, serviceSpanNameIdx, span)
        indexByAnnotations(annotationsIdx, span)
        indexSpanDuration(durationIdx, span)
      }
    }

    Future.collect(Seq(
      traces,
      serviceNames,
      spanNames,
      serviceNameIdx,
      serviceSpanNameIdx,
      annotationsIdx,
      durationIdx
    ).map(_.execute())).unit
  }

  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
    Traces.getRow(traceId) flatMap { row =>
      val traces = Traces.batch()
      row.values.asScala foreach { col =>
        traces.insert(traceId, col.copy(timestamp = None, ttl = Some(ttl)))
      }
      traces.execute().unit
    }
  }

  def getTimeToLive(traceId: Long): Future[Duration] = {
    QueryGetTtlCounter.incr()
    Traces.getRow(traceId) flatMap { row =>
      val ttl = row.values.asScala.foldLeft(Int.MaxValue) { (ttl, col) =>
        math.min(ttl, col.ttl.map(_.inSeconds).getOrElse(Int.MaxValue))
      }

      if (ttl == Int.MaxValue)
        Future.exception(new IllegalArgumentException("The trace " + traceId + " does not have any ttl set!"))
      else
        Future.value(ttl.seconds)
    }
  }

  override def getDataTimeToLive = Future.value(spanTtl.inSeconds)

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    QueryTracesExistStat.add(traceIds.size)
    getSpansByTraceIds(traceIds, 1) map {
      _.flatMap(_.headOption.map(_.traceId)).toSet
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
    ServiceNames.getRow(ServiceNamesKey).map(_.values.asScala.map(_.name).toSet)
  }

  def getSpanNames(service: String): Future[Set[String]] = {
    QueryGetSpanNamesCounter.incr()
    SpanNames.getRow(service).map(_.values.asScala.map(_.name).toSet)
  }

  def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = {
    QueryGetTraceIdsByNameCounter.incr()
    val key = nameKey(serviceName, spanName)

    // if we have a span name, look up in the service + span name index
    // if not, look up by service name only
    val idx: ColumnFamily[String, Long, Long] =
      spanName.map(_ => ServiceSpanNameIndex).getOrElse(ServiceNameIndex)
    // TODO: endTs seems wrong here
    idx.getRowSlice(key, Some(endTs), None, limit, Order.Reversed) map colToIndexedTraceId
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
    AnnotationsIndex.getRowSlice(key, None, Some(endTs), limit, Order.Reversed) map colToIndexedTraceId
  }

  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
    QueryGetTracesDurationStat.add(traceIds.size)

    val traceIdSet = traceIds.toSet.asJava

    Future.collect(Seq(
      DurationIndex.multigetRows(traceIdSet, None, None, Order.Normal, 1),
      DurationIndex.multigetRows(traceIdSet, None, None, Order.Reversed, 1)
    )) map { results =>
      val Seq(startRows, endRows) = results map { rows =>
        rows.asScala.toSeq map { case (traceId, cols) =>
          cols.asScala.headOption map { case (_, col) => (traceId, col.name) }
        }
      }
      startRows zip(endRows) collect {
        case (Some((startId, startTs)), Some((endId, endTs))) if (startId == endId) =>
          TraceIdDuration(endId, endTs - startTs, startTs)
      }
    }
  }
}
