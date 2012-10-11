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
import com.twitter.finagle.stats.{StatsReceiver, NullStatsReceiver}
import com.twitter.finagle.tracing.{Trace => FTrace}
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.Service
import com.twitter.util.Future
import com.twitter.zipkin.adapter.ThriftQueryAdapter
import com.twitter.zipkin.gen
import com.twitter.zipkin.query.adjusters.Adjuster
import com.twitter.zipkin.storage._
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.thrift.TException
import scala.collection.Set

/**
 * Able to respond to users queries regarding the traces. Usually does so
 * by lookup the information in the index and then fetch the required trace data
 * from the storage.
 */
class QueryService(storage: Storage, index: Index, aggregates: Aggregates, adjusterMap: Map[gen.Adjust, Adjuster],
                   statsReceiver: StatsReceiver = NullStatsReceiver) extends gen.ZipkinQuery.FutureIface with Service {
  private val log = Logger.get
  private val running = new AtomicBoolean(false)

  private val stats = statsReceiver.scope("QueryService")
  private val methodStats = stats.scope("methods")
  private val errorStats = stats.scope("errors")
  private val timingStats = stats.scope("timing")

  // how to sort the trace summaries
  private val OrderByDurationDesc = {
    (a: TraceIdDuration, b: TraceIdDuration) => a.duration > b.duration
  }
  private val OrderByDurationAsc = {
    (a: TraceIdDuration, b: TraceIdDuration) => a.duration < b.duration
  }
  private val OrderByTimestampDesc = {
    (a: TraceIdDuration, b: TraceIdDuration) => a.startTimestamp > b.startTimestamp
  }
  private val OrderByTimestampAsc = {
    (a: TraceIdDuration, b: TraceIdDuration) => a.startTimestamp < b.startTimestamp
  }

  // this is how many trace durations we fetch in one request
  // TODO config
  var traceDurationFetchBatchSize = 500

  def start() {
    running.set(true)
  }

  def shutdown() {
    running.set(false)
    storage.close
  }

  private def constructQueryResponse(indexedIds: Seq[IndexedTraceId], limit: Int, order: gen.Order, defaultEndTs: Long = -1): Future[gen.QueryResponse] = {
    val ids = indexedIds.map { _.traceId }
    val ts = indexedIds.map { _.timestamp }

    sortTraceIds(Future(ids), limit, order).map { sortedIds =>
      val (min, max) = sortedIds match {
        case Nil => (-1L, defaultEndTs)
        case _   => (ts.min, ts.max)
      }
      gen.QueryResponse(sortedIds, min, max)
    }
  }

  def getTraceIds(queryRequest: gen.QueryRequest): Future[gen.QueryResponse] = {
    val method = "getTraceIds"
    log.debug("%s: %s".format(method, queryRequest.toString))
    call(method) {
      val serviceName = queryRequest.`serviceName`
      val spanName = queryRequest.`spanName`
      val endTs = queryRequest.`endTs`
      val limit = queryRequest.`limit`
      val order = queryRequest.`order`

      val sliceQueries = Seq(
        spanName.map { name =>
          Seq(SpanSliceQuery(serviceName, name, endTs, limit))
        },
        queryRequest.`annotations`.map {
          _.map { a =>
            AnnotationSliceQuery(serviceName, a, None, endTs, limit)
          }
        },
        queryRequest.`binaryAnnotations`.map {
          _.map { b =>
            AnnotationSliceQuery(serviceName, b.`key`, Some(b.`value`), endTs, limit)
          }
        }
      ).collect {
        case Some(q: Seq[SliceQuery]) => q
      }.flatten

      log.debug(sliceQueries.toString())

      sliceQueries match {
        case Nil => {
          /* No queries: get service level traces */
          index.getTraceIdsByName(serviceName, None, endTs, limit).map {
            constructQueryResponse(_, limit, order)
          }.flatten
        }
        case head :: Nil => {
          /* One query: just run it */
          head.execute(index).map {
            constructQueryResponse(_, limit, order)
          }.flatten
        }
        case queries => {
          /* Multiple: Fetch a single column from each to reconcile non-overlapping portions
             then fetch the entire slice */
          Future.collect {
            queries.map {
              _.execute(index)
            }
          }.map {
            _.flatten.map {
              _.timestamp
            }.min
          }.map { alignedTimestamp =>
            /* Pad the aligned timestamp by a minute */
            val ts = padTimestamp(alignedTimestamp)

            Future.collect {
              queries.map {
                case s: SpanSliceQuery => s.copy(endTs = ts, limit = limit).execute(index)
                case a: AnnotationSliceQuery => a.copy(endTs = ts, limit = limit).execute(index)
              }
            }.map { ids =>
              traceIdsIntersect(ids) match {
                case Nil => {
                  val endTimestamp = ids.map {
                    _.map { _.timestamp }.min
                  }.max
                  constructQueryResponse(Nil, limit, order, endTimestamp)
                }
                case seq => {
                  constructQueryResponse(seq, limit, order)
                }
              }

            }
          }.flatten.flatten
        }
      }
    }
  }

  private[query] def padTimestamp(timestamp: Long): Long = timestamp + Constants.TraceTimestampPadding.inMicroseconds

  private[query] def traceIdsIntersect(idSeqs: Seq[Seq[IndexedTraceId]]): Seq[IndexedTraceId] = {
    /* Find the trace IDs present in all the Seqs */
    val idMaps = idSeqs.map {
      _.groupBy {
        _.traceId
      }
    }
    val traceIds = idMaps.map {
      _.keys.toSeq
    }
    val commonTraceIds = traceIds.tail.fold(traceIds(0)) { _.intersect(_) }

    /*
     * Find the timestamps associated with each trace ID and construct a new IndexedTraceId
     * that has the trace ID's maximum timestamp (ending) as the timestamp
     */
    commonTraceIds.map { id =>
      val maxTime = idMaps.map { m =>
        m(id).map { _.timestamp }
      }.flatten.max
      IndexedTraceId(id, maxTime)
    }
  }

  def getTraceIdsBySpanName(serviceName: String, spanName: String, endTs: Long,
                        limit: Int, order: gen.Order): Future[Seq[Long]] = {
    val method = "getTraceIdsBySpanName"
    log.debug("%s. serviceName: %s spanName: %s endTs: %s limit: %s order: %s".format(method, serviceName, spanName,
      endTs, limit, order))
    call(method) {
      if (serviceName == null || "".equals(serviceName)) {
        errorStats.counter("%s_no_service".format(method)).incr()
        return Future.exception(gen.QueryException("No service name provided"))
      }

      // do we have a valid span name to query indexes by?
      val span = convertToOption(spanName)

      FTrace.recordBinary("serviceName", serviceName)
      FTrace.recordBinary("spanName", spanName)
      FTrace.recordBinary("endTs", endTs)
      FTrace.recordBinary("limit", limit)
      FTrace.recordBinary("order", order)

      val traceIds = index.getTraceIdsByName(serviceName, span, endTs, limit).map {
        _.map { _.traceId }
      }
      sortTraceIds(traceIds, limit, order)
    }
  }

  def getTraceIdsByServiceName(serviceName: String, endTs: Long,
                               limit: Int, order: gen.Order): Future[Seq[Long]] = {
    val method = "getTraceIdsByServiceName"
    log.debug("%s. serviceName: %s endTs: %s limit: %s order: %s".format(method, serviceName, endTs, limit, order))
    call(method) {
      if (serviceName == null || "".equals(serviceName)) {
        errorStats.counter("%s_no_service".format(method)).incr()
        return Future.exception(gen.QueryException("No service name provided"))
      }

      FTrace.recordBinary("serviceName", serviceName)
      FTrace.recordBinary("endTs", endTs)
      FTrace.recordBinary("limit", limit)
      FTrace.recordBinary("order", order)

      val traceIds = index.getTraceIdsByName(serviceName, None, endTs, limit).map {
        _.map { _.traceId }
      }
      sortTraceIds(traceIds, limit, order)
    }
  }


  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: ByteBuffer, endTs: Long,
                              limit: Int, order: gen.Order): Future[Seq[Long]] = {
    val method = "getTraceIdsByAnnotation"
    log.debug("%s. serviceName: %s annotation: %s value: %s endTs: %s limit: %s order: %s".format(method, serviceName,
      annotation, value, endTs, limit, order))
    call(method) {
      if (annotation == null || "".equals(annotation)) {
        errorStats.counter("%s_no_annotation".format(method)).incr()
        return Future.exception(gen.QueryException("No annotation provided"))
      }

      // do we have a valid annotation value to query indexes by?
      val valueOption = convertToOption(value)

      FTrace.recordBinary("serviceName", serviceName)
      FTrace.recordBinary("annotation", annotation)
      FTrace.recordBinary("endTs", endTs)
      FTrace.recordBinary("limit", limit)
      FTrace.recordBinary("order", order)

      val traceIds = index.getTraceIdsByAnnotation(serviceName, annotation, valueOption, endTs, limit).map {
        _.map { _.traceId }
      }
      sortTraceIds(traceIds, limit, order)
    }
  }

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    log.debug("tracesExist. " + traceIds)
    call("tracesExist") {
      FTrace.recordBinary("numIds", traceIds.length)

      storage.tracesExist(traceIds)
    }
  }

  def getTracesByIds(traceIds: Seq[Long], adjust: Seq[gen.Adjust]): Future[Seq[gen.Trace]] = {
    log.debug("getTracesByIds. " + traceIds + " adjust " + adjust)
    call("getTracesByIds") {
      val adjusters = getAdjusters(adjust)
      FTrace.recordBinary("numIds", traceIds.length)

      storage.getSpansByTraceIds(traceIds).map { traces =>
        traces.map { spans =>
          val trace = Trace(spans)
          ThriftQueryAdapter(adjusters.foldLeft(trace)((t, adjuster) => adjuster.adjust(t)))
        }
      }
    }
  }

  def getTraceTimelinesByIds(traceIds: Seq[Long],
                             adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceTimeline]] = {
    log.debug("getTraceTimelinesByIds. " + traceIds + " adjust " + adjust)
    call("getTraceTimelinesByIds") {
      val adjusters = getAdjusters(adjust)
      FTrace.recordBinary("numIds", traceIds.length)

      storage.getSpansByTraceIds(traceIds).map { traces =>
        traces.flatMap { spans =>
          val trace = Trace(spans)
          TraceTimeline(adjusters.foldLeft(trace)((t, adjuster) => adjuster.adjust(t))).map(ThriftQueryAdapter(_))
        }
      }
    }
  }

  def getTraceSummariesByIds(traceIds: Seq[Long],
                             adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceSummary]] = {
    log.debug("getTraceSummariesByIds. traceIds: " + traceIds + " adjust " + adjust)
    call("getTraceSummariesByIds") {
      val adjusters = getAdjusters(adjust)
      FTrace.recordBinary("numIds", traceIds.length)

      storage.getSpansByTraceIds(traceIds.toList).map { traces =>
        traces.flatMap { spans =>
          val trace = Trace(spans)
          TraceSummary(adjusters.foldLeft(trace)((t, adjuster) => adjuster.adjust(t))).map(ThriftQueryAdapter(_))
        }
      }
    }
  }

  def getTraceCombosByIds(traceIds: Seq[Long], adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceCombo]] = {
    log.debug("getTraceComboByIds. traceIds: " + traceIds + " adjust " + adjust)
    call("getTraceComboByIds") {
      val adjusters = getAdjusters(adjust)
      FTrace.recordBinary("numIds", traceIds.length)

      storage.getSpansByTraceIds(traceIds).map { traces =>
        traces.map { spans =>
          val trace = Trace(spans)
          ThriftQueryAdapter(TraceCombo(adjusters.foldLeft(trace)((t, adjuster) => adjuster.adjust(t))))
        }
      }
    }
  }

  def getDataTimeToLive: Future[Int] = {
    log.debug("getDataTimeToLive")
    call("getDataTimeToLive") {
      Future(storage.getDataTimeToLive)
    }
  }

  def getServiceNames: Future[Set[String]] = {
    log.debug("getServiceNames")
    call("getServiceNames") {
      index.getServiceNames
    }
  }

  def getSpanNames(service: String): Future[Set[String]] = {
    log.debug("getSpanNames")
    call("getSpanNames") {
      index.getSpanNames(service)
    }
  }

  def setTraceTimeToLive(traceId: Long, ttlSeconds: Int): Future[Unit] = {
    log.debug("setTimeToLive: " + traceId + " " + ttlSeconds)
    call("setTraceTimeToLive") {
      storage.setTimeToLive(traceId, ttlSeconds.seconds)
    }
  }

  def getTraceTimeToLive(traceId: Long): Future[Int] = {
    log.debug("getTimeToLive: " + traceId)
    call("getTraceTimeToLive") {
      storage.getTimeToLive(traceId).map(_.inSeconds)
    }
  }

  def getDependencies(serviceName: String): Future[Seq[String]] = {
    log.debug("getDependencies: " + serviceName)
    call("getDependencies") {
      aggregates.getDependencies(serviceName)
    }
  }

  def getTopAnnotations(serviceName: String): Future[Seq[String]] = {
    log.debug("getTopAnnotations: " + serviceName)
    call("getTopAnnotations") {
      aggregates.getTopAnnotations(serviceName)
    }
  }

  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]] = {
    log.debug("getTopKeyValueAnnotations: " + serviceName)
    call("getTopKeyValueAnnotations") {
      aggregates.getTopKeyValueAnnotations(serviceName)
    }
  }

  private def checkIfRunning() = {
    if (!running.get) {
      log.warning("Server not running, throwing exception")
      throw new TException("Server not running")
    }
  }

  private[this] def call[T](name: String)(f: => Future[T]): Future[T] = {
    checkIfRunning()
    methodStats.counter(name).incr()

    timingStats.timeFuture(name) {
      f rescue {
        case e: Exception => {
          log.error(e, "%s failed".format(name))
          errorStats.counter(name).incr()
          Future.exception(gen.QueryException(e.toString))
        }
      }
    }
  }

  /**
   * Convert incoming Thrift order by enum into sort function.
   */
  private def getOrderBy(order: gen.Order) = {
    order match {
      case gen.Order.DurationDesc => OrderByDurationDesc
      case gen.Order.DurationAsc => OrderByDurationAsc
      case gen.Order.TimestampDesc => OrderByTimestampDesc
      case gen.Order.TimestampAsc => OrderByTimestampAsc
    }
  }

  private def getAdjusters(adjusters: Seq[gen.Adjust]): Seq[Adjuster] = {
    adjusters.flatMap { adjusterMap.get(_) }
  }

  /**
   * Do we have a valid object to query indexes by?
   */
  private def convertToOption[O](param: O): Option[O] = {
    param match {
      case null => None
      case "" => None
      case s => Some(s)
    }
  }

  /**
   * Given a sequence of traceIds get their durations
   */
  private def getTraceIdDurations(
    traceIds: Future[Seq[Long]]
  ): Future[Seq[TraceIdDuration]] = {
    traceIds.map { t =>
      Future.collect {
        t.grouped(traceDurationFetchBatchSize)
        .toSeq
        .map {index.getTracesDuration(_)}
      }
    }.flatten.map {_.flatten}
  }

  private def sortTraceIds(
    traceIds: Future[Seq[Long]],
    limit: Int,
    order: gen.Order
  ): Future[Seq[Long]] = {

    // No sorting wanted
    if (order == gen.Order.None) {
      traceIds
    } else {
      val durations = getTraceIdDurations(traceIds)
      durations map { d =>
        d.sortWith(getOrderBy(order)).slice(0, limit).map(_.traceId)
      }
    }
  }
}
