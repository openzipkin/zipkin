/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.query

import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.{Trace => FTrace}
import com.twitter.logging.Logger
import com.twitter.util.{Future, Time}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.query.adjusters._
import com.twitter.zipkin.query.constants._
import com.twitter.zipkin.storage._
import com.twitter.zipkin.{gen => thrift}
import java.nio.ByteBuffer

class ThriftQueryService(
  spanStore: SpanStore,
  aggsStore: Aggregates = new NullAggregates,
  realtimeStore: RealtimeAggregates = NullRealtimeAggregates,
  adjusters: Map[thrift.Adjust, Adjuster] = Map.empty[thrift.Adjust, Adjuster],
  traceDurationFetchBatchSize: Int = 500,
  stats: StatsReceiver = DefaultStatsReceiver.scope("ThriftQueryService"),
  log: Logger = Logger.get("ThriftQueryService")
) extends thrift.ZipkinQuery[Future] {

  private[this] val methodStats = stats.scope("perMethod")

  private[this] def opt[T](param: T): Option[T] = param match {
    case null | "" => None
    case s => Some(s)
  }

  private[this] def getTraceIdDurations(fIds: Future[Seq[Long]]): Future[Seq[TraceIdDuration]] = {
    fIds flatMap { ids =>
      val ret = ids.grouped(traceDurationFetchBatchSize).toSeq.map(spanStore.getTracesDuration(_))
      Future.collect(ret).map(_.flatten)
    }
  }

  private[this] def sortedTraceIds(traceIds: Future[Seq[IndexedTraceId]], limit: Int, order: thrift.Order): Future[Seq[Long]] = {
    order match {
      case thrift.Order.None =>
        traceIds.map(_.slice(0, limit).map(_.traceId))

      case thrift.Order.TimestampDesc | thrift.Order.TimestampAsc =>
        val orderBy = order match {
          case thrift.Order.TimestampDesc => (a: IndexedTraceId, b: IndexedTraceId) => a.timestamp > b.timestamp
          case thrift.Order.TimestampAsc => (a: IndexedTraceId, b: IndexedTraceId) => a.timestamp < b.timestamp
          case _ => throw new Exception("what?")
        }
        traceIds.map { _.sortWith(orderBy).slice(0, limit).map(_.traceId) }

      case thrift.Order.DurationDesc | thrift.Order.DurationAsc =>
        val orderBy = order match {
          case thrift.Order.DurationDesc => (a: TraceIdDuration, b: TraceIdDuration) => a.duration > b.duration
          case thrift.Order.DurationAsc => (a: TraceIdDuration, b: TraceIdDuration) => a.duration < b.duration
          case _ => throw new Exception("what?")
        }
        getTraceIdDurations(traceIds.map(_.map(_.traceId))) map { _.sortWith(orderBy).slice(0, limit).map(_.traceId) }
    }
  }

  private[this] def sort(traces: Future[Seq[IndexedTraceId]], limit: Int, order: thrift.Order): Future[Seq[Long]] =
    sortedTraceIds(traces, limit, order)

  private[this] def adjustedTraces(traces: Seq[Seq[Span]], adjusts: Seq[thrift.Adjust]): Seq[Trace] = {
    val as = adjusts flatMap { adjusters.get(_) }
    traces map { spans =>
      as.foldLeft(Trace(spans)) { (t, adjuster) => adjuster.adjust(t) }
    }
  }

  private[this] def padTimestamp(timestamp: Long): Long =
    timestamp + TraceTimestampPadding.inMicroseconds

  private[this] def traceIdsIntersect(idSeqs: Seq[Seq[IndexedTraceId]]): Seq[IndexedTraceId] = {
    /* Find the trace IDs present in all the Seqs */
    val idMaps = idSeqs.map(_.groupBy(_.traceId))
    val traceIds = idMaps.map(_.keys.toSeq)
    val commonTraceIds = traceIds.tail.fold(traceIds(0))(_.intersect(_))

    /*
     * Find the timestamps associated with each trace ID and construct a new IndexedTraceId
     * that has the trace ID's maximum timestamp (ending) as the timestamp
     */
    commonTraceIds map { id =>
      IndexedTraceId(id, idMaps.flatMap(_(id).map(_.timestamp)).max)
    }
  }

  private[this] def queryResponse(
    ids: Seq[IndexedTraceId],
    qr: thrift.QueryRequest,
    endTs: Long = -1
  ): Future[thrift.QueryResponse] = {
    sortedTraceIds(Future.value(ids), qr.limit, qr.order) map { sortedIds =>
      val (min, max) = sortedIds match {
        case Nil =>
          (-1L, endTs)
        case _   =>
          val ts = ids.map(_.timestamp)
          (ts.min, ts.max)
      }
      thrift.QueryResponse(sortedIds, min, max)
    }
  }

  private trait SliceQuery
  private case class SpanSliceQuery(name: String) extends SliceQuery
  private case class AnnotationSliceQuery(key: String, value: Option[ByteBuffer]) extends SliceQuery

  private[this] def querySlices(slices: Seq[SliceQuery], qr: thrift.QueryRequest): Future[Seq[Seq[IndexedTraceId]]] =
    Future.collect(slices map {
      case SpanSliceQuery(name) =>
        spanStore.getTraceIdsByName(qr.serviceName, Some(name), qr.endTs, qr.limit)
      case AnnotationSliceQuery(key, value) =>
        spanStore.getTraceIdsByAnnotation(qr.serviceName, key, value, qr.endTs, qr.limit)
      case s =>
        Future.exception(new Exception("Uknown SliceQuery: %s".format(s)))
    })

  private[this] def handle[T](name: String)(f: => Future[T]): Future[T] = {
    val errorStats = methodStats.scope("errors")

    val ret = try {
      methodStats.timeFuture(name)(f)
    } catch {
      case e: Exception => Future.exception(e)
    }

    ret rescue { case e: Exception =>
      log.error(e, "%s error".format(name))
      errorStats.counter(name).incr()
      errorStats.scope(name).counter(e.getClass.getName).incr()
      Future.exception(thrift.QueryException(e.toString))
    }
  }

  private[this] val noServiceNameError = Future.exception(thrift.QueryException("No service name provided"))
  private[this] def handleQuery[T](name: String, qr: thrift.QueryRequest)(f: => Future[T]): Future[T] =
    if (!opt(qr.serviceName).isDefined) noServiceNameError else {
      FTrace.recordBinary("serviceName", qr.serviceName)
      FTrace.recordBinary("endTs", qr.endTs)
      FTrace.recordBinary("limit", qr.limit)
      FTrace.recordBinary("order", qr.order)
      handle(name)(f)
    }

  def getTraceIds(qr: thrift.QueryRequest): Future[thrift.QueryResponse] =
    handleQuery("getTraceIds", qr) {
      val sliceQueries = Seq[Option[Seq[SliceQuery]]](
        qr.spanName.map { n => Seq(SpanSliceQuery(n)) },
        qr.annotations.map { _.map { AnnotationSliceQuery(_, None) } },
        qr.binaryAnnotations.map { _.map { b => AnnotationSliceQuery(b.key, Some(b.value)) } }
      ).flatten.flatten

      sliceQueries match {
        case Nil =>
          spanStore.getTraceIdsByName(qr.serviceName, None, qr.endTs, qr.limit) flatMap {
            queryResponse(_, qr)
          }

        case slice :: Nil =>
          querySlices(sliceQueries, qr) flatMap { ids => queryResponse(ids.flatten, qr) }

        case _ =>
          // TODO: timestamps endTs is the wrong name for all this
          querySlices(sliceQueries, qr.copy(limit = 1)) flatMap { ids =>
            val ts = padTimestamp(ids.flatMap(_.map(_.timestamp)).reduceOption(_ min _).getOrElse(0))
            querySlices(sliceQueries, qr.copy(endTs = ts)) flatMap { ids =>
              traceIdsIntersect(ids) match {
                case Nil =>
                  val endTs = ids.map(_.map(_.timestamp).reduceOption(_ min _).getOrElse(0L)).reduceOption(_ max _).getOrElse(0L)
                  queryResponse(Nil, qr, endTs)
                case seq =>
                  queryResponse(seq, qr)
              }
            }
          }
      }
    }

  def getTraceIdsBySpanName(
    serviceName: String,
    spanName: String,
    endTs: Long,
    limit: Int,
    order: thrift.Order
  ): Future[Seq[Long]] = {
    val qr = thrift.QueryRequest(serviceName, opt(spanName), None, None, endTs, limit, order)
    handleQuery("getTraceIdsBySpanName", qr) {
      sort(spanStore.getTraceIdsByName(serviceName, qr.spanName, endTs, limit), limit, order)
    }
  }

  def getTraceIdsByServiceName(
    serviceName: String,
    endTs: Long,
    limit: Int,
    order: thrift.Order
  ): Future[Seq[Long]] = {
    val qr = thrift.QueryRequest(serviceName, None, None, None, endTs, limit, order)
    handleQuery("getTraceIdsBySpanName", qr) {
      sort(spanStore.getTraceIdsByName(serviceName, None, endTs, limit), limit, order)
    }
  }

  def getTraceIdsByAnnotation(
    serviceName: String,
    key: String,
    value: ByteBuffer,
    endTs: Long,
    limit: Int,
    order: thrift.Order
  ): Future[Seq[Long]] = {
    val qr = thrift.QueryRequest(serviceName, None, None, None, endTs, limit, order)
    handleQuery("getTraceIdsByAnnotation", qr) {
      sort(spanStore.getTraceIdsByAnnotation(serviceName, key, opt(value), endTs, limit), limit, order)
    }
  }

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] =
    handle("tracesExist") {
      FTrace.recordBinary("numIds", traceIds.length)
      spanStore.tracesExist(traceIds)
    }

  def getTracesByIds(traceIds: Seq[Long], adjust: Seq[thrift.Adjust]): Future[Seq[thrift.Trace]] =
    handle("getTracesByIds") {
      FTrace.recordBinary("numIds", traceIds.length)
      spanStore.getSpansByTraceIds(traceIds) map { adjustedTraces(_, adjust).map(_.toThrift) }
    }

  def getTraceTimelinesByIds(traceIds: Seq[Long], adjust: Seq[thrift.Adjust]): Future[Seq[thrift.TraceTimeline]] =
    handle("getTraceTimelinesByIds") {
      FTrace.recordBinary("numIds", traceIds.length)
      spanStore.getSpansByTraceIds(traceIds) map { traces =>
        adjustedTraces(traces, adjust) flatMap { TraceTimeline(_).map(_.toThrift) }
      }
    }

  def getTraceSummariesByIds(traceIds: Seq[Long], adjust: Seq[thrift.Adjust]): Future[Seq[thrift.TraceSummary]] =
    handle("getTraceSummariesByIds") {
      FTrace.recordBinary("numIds", traceIds.length)
      spanStore.getSpansByTraceIds(traceIds) map { traces =>
        adjustedTraces(traces, adjust) flatMap { TraceSummary(_).map(_.toThrift) }
      }
    }

  def getTraceCombosByIds(traceIds: Seq[Long], adjust: Seq[thrift.Adjust]): Future[Seq[thrift.TraceCombo]] =
    handle("getTraceCombosByIds") {
      FTrace.recordBinary("numIds", traceIds.length)
      spanStore.getSpansByTraceIds(traceIds) map { traces =>
        adjustedTraces(traces, adjust) map { TraceCombo(_).toThrift }
      }
    }

  // TODO
  def getDataTimeToLive: Future[Int] =
    handle("getDataTimeToLive") {
      Future.exception(new Exception("not implemented"))
    }

  def getServiceNames: Future[Set[String]] =
    handle("getServiceNames") {
      spanStore.getAllServiceNames
    }

  def getSpanNames(serviceName: String): Future[Set[String]] =
    handle("getSpanNames") {
      spanStore.getSpanNames(serviceName)
    }

  def setTraceTimeToLive(traceId: Long, ttl: Int): Future[Unit] =
    handle("setTraceTimeToLive") {
      spanStore.setTimeToLive(traceId, ttl.seconds)
    }

  def getTraceTimeToLive(traceId: Long): Future[Int] =
    handle("getTraceTimeToLive") {
      spanStore.getTimeToLive(traceId).map(_.inSeconds)
    }

  def getDependencies(startTime: Option[Long], endTime: Option[Long]) : Future[thrift.Dependencies] =
    handle("getDependencies") {
      val start = startTime map { Time.fromMicroseconds(_) }
      val end = endTime map { Time.fromMicroseconds(_) }
      aggsStore.getDependencies(start, end).map(_.toThrift)
    }

  def getTopAnnotations(serviceName: String): Future[Seq[String]] =
    handle("getTopAnnotations") {
      aggsStore.getTopAnnotations(serviceName)
    }

  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]] =
    handle("getTopKeyValueAnnotations") {
      aggsStore.getTopKeyValueAnnotations(serviceName)
    }

  def getSpanDurations(
    timeStamp: Long,
    serverServiceName: String,
    rpcName: String
  ): Future[Map[String, List[Long]]] =
    handle("getSpanDurations") {
      val time = Time.fromMicroseconds(timeStamp)
      realtimeStore.getSpanDurations(time, serverServiceName, rpcName)
    }

  def getServiceNamesToTraceIds(
    timeStamp: Long,
    serverServiceName: String,
    rpcName: String
  ): Future[Map[String, List[Long]]] =
    handle("getServiceNamesToTraceIds") {
      val time = Time.fromMicroseconds(timeStamp)
      realtimeStore.getServiceNamesToTraceIds(time, serverServiceName, rpcName)
    }
}
