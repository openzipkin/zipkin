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
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.ostrich.admin.Service
import com.twitter.finagle.tracing.Trace
import com.twitter.util.Future
import com.twitter.zipkin.gen
import com.twitter.zipkin.query.adjusters.Adjuster
import com.twitter.zipkin.storage.{TraceIdDuration, Index, Storage}
import java.nio.ByteBuffer
import org.apache.thrift.TException
import scala.collection.Set

/**
 * Able to respond to users queries regarding the traces. Usually does so
 * by lookup the information in the index and then fetch the required trace data
 * from the storage.
 */
class QueryService(storage: Storage, index: Index, adjusterMap: Map[gen.Adjust, Adjuster])
  extends gen.ZipkinQuery.FutureIface with Service {
  private val log = Logger.get
  private var running = false

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
    running = true
  }

  def shutdown() {
    running = false
    storage.close
  }

  def getTraceIdsBySpanName(serviceName: String, spanName: String, endTs: Long,
                        limit: Int, order: gen.Order): Future[Seq[Long]] = {
    checkIfRunning
    Stats.incr("query.get_trace_ids_name")
    log.debug("getTraceIdsByName. serviceName: " + serviceName + " spanName: " + spanName +
      " endTs: " + endTs + " limit: " + limit + " order:" + order)

    if (serviceName == null || "".equals(serviceName)) {
      Stats.incr("query.error_get_trace_ids_name_no_service")
      return Future.exception(gen.QueryException("No service name provided, we need one"))
    }

    // do we have a valid span name to query indexes by?
    val span = convertToOption(spanName)

    Trace.recordBinary("serviceName", serviceName)
    Trace.recordBinary("spanName", spanName)
    Trace.recordBinary("endTs", endTs)
    Trace.recordBinary("limit", limit)
    Trace.recordBinary("order", order)

    Stats.timeFutureMillis("query.getTraceIdsByName") {
      {
        val traceIds = index.getTraceIdsByName(serviceName, span, endTs, limit)
        sortTraceIds(traceIds, limit, order)
      } rescue {
        case e: Exception => {
          log.error(e, "getTraceIdsByName query failed")
          Stats.incr("query.error_get_trace_ids_name_exception")
          Future.exception(gen.QueryException(e.toString))
        }
      }
    }
  }

  def getTraceIdsByServiceName(serviceName: String, endTs: Long,
                               limit: Int, order: gen.Order): Future[Seq[Long]] = {
    checkIfRunning
    Stats.incr("query.get_trace_ids_by_service_name")

    log.debug("getTraceIdsByServiceName. serviceName: " + serviceName + " endTs: " +
      endTs + " limit: " + limit + " order:" + order)

    if (serviceName == null || "".equals(serviceName)) {
      Stats.incr("query.error_get_trace_ids_by_service_name_no_service")
      return Future.exception(gen.QueryException("No service name provided, we need one"))
    }

    Trace.recordBinary("serviceName", serviceName)
    Trace.recordBinary("endTs", endTs)
    Trace.recordBinary("limit", limit)
    Trace.recordBinary("order", order)

    Stats.timeFutureMillis("query.getTraceIdsByServiceName") {
      {
        val traceIds = index.getTraceIdsByName(serviceName, None, endTs, limit)
        sortTraceIds(traceIds, limit, order)
      } rescue {
        case e: Exception =>
          log.error(e, "getTraceIdsByServiceName query failed")
          Stats.incr("query.error_get_trace_ids_by_service_name_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }


  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: ByteBuffer, endTs: Long,
                              limit: Int, order: gen.Order): Future[Seq[Long]] = {
    checkIfRunning
    Stats.incr("query.get_trace_ids_by_annotation")

    log.debug("getTraceIdsByAnnotation. serviceName: " + serviceName + " annotation: " + annotation + " value: " + value +
      " endTs: " + endTs + " limit: " + limit + " order:" + order)

    if (annotation == null || "".equals(annotation)) {
      Stats.incr("query.error_get_trace_ids_by_annotation_no_annotation")
      return Future.exception(gen.QueryException("No annotation provided, we need one"))
    }

    // do we have a valid annotation value to query indexes by?
    val valueOption = convertToOption(value)

    Trace.recordBinary("serviceName", serviceName)
    Trace.recordBinary("annotation", annotation)
    Trace.recordBinary("endTs", endTs)
    Trace.recordBinary("limit", limit)
    Trace.recordBinary("order", order)

    Stats.timeFutureMillis("query.getTraceIdsByAnnotation") {
      {
        val traceIds = index.getTraceIdsByAnnotation(serviceName, annotation, valueOption, endTs, limit)
        sortTraceIds(traceIds, limit, order)
      } rescue {
        case e: Exception =>
          log.error(e, "getTraceIdsByAnnotation query failed")
          Stats.incr("query.error_get_trace_ids_by_annotation_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getTracesByIds(traceIds: Seq[Long], adjust: Seq[gen.Adjust]): Future[Seq[gen.Trace]] = {
    checkIfRunning
    Stats.incr("query.get_trace_by_id")
    log.debug("getTracesByIds. " + traceIds + " adjust " + adjust)

    val adjusters = getAdjusters(adjust)

    Trace.recordBinary("numIds", traceIds.length)

    Stats.timeFutureMillis("query.getTracesByIds") {
      storage.getTracesByIds(traceIds).map { id =>
        id.map(adjusters.foldLeft(_)((trace, adjuster) => adjuster.adjust(trace)).toThrift)
      } rescue {
        case e: Exception =>
          log.error(e, "getTracesByIds query failed")
          Stats.incr("query.error_get_trace_by_id_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getTraceTimelinesByIds(traceIds: Seq[Long],
                             adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceTimeline]] = {
    checkIfRunning
    Stats.incr("query.get_trace_timelines_by_ids")
    log.debug("getTraceTimelinesByIds. " + traceIds + " adjust " + adjust)

    val adjusters = getAdjusters(adjust)

    Trace.recordBinary("numIds", traceIds.length)

    Stats.timeFutureMillis("query.getTraceTimelinesByIds") {
      storage.getTracesByIds(traceIds).map { id =>
        id.flatMap(adjusters.foldLeft(_)((trace, adjuster) => adjuster.adjust(trace)).toTimeline)
      } rescue {
        case e: Exception =>
          log.error(e, "getTraceTimelinesByIds query failed")
          Stats.incr("query.error_get_trace_timelines_by_ids_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getTraceSummariesByIds(traceIds: Seq[Long],
                             adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceSummary]] = {
    checkIfRunning
    Stats.incr("query.get_traces_summary_id")
    log.debug("getTraceSummariesByIds. traceIds: " + traceIds + " adjust " + adjust)

    val adjusters = getAdjusters(adjust)

    Trace.recordBinary("numIds", traceIds.length)

    Stats.timeFutureMillis("query.getTraceSummariesByIds") {
      storage.getTracesByIds(traceIds.toList).map { id =>
        id.flatMap(adjusters.foldLeft(_)((trace, adjuster) => adjuster.adjust(trace)).toTraceSummary.map(_.toThrift))
      } rescue {
        case e: Exception =>
          log.error(e, "getTraceSummariesByIds query failed")
          Stats.incr("query.error_get_traces_summary_id_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getTraceCombosByIds(traceIds: Seq[Long], adjust: Seq[gen.Adjust]): Future[Seq[gen.TraceCombo]] = {
    checkIfRunning
    Stats.incr("query.get_trace_combo_by_ids")
    log.debug("getTraceComboByIds. traceIds: " + traceIds + " adjust " + adjust)

    val adjusters = getAdjusters(adjust)

    Trace.recordBinary("numIds", traceIds.length)

    Stats.timeFutureMillis("query.getTraceComboByIds") {
      storage.getTracesByIds(traceIds).map { id =>
        id.map(adjusters.foldLeft(_)((trace, adjuster) => adjuster.adjust(trace)).toTraceCombo)
      } rescue {
        case e: Exception =>
          log.error(e, "getTraceCombosByIds query failed")
          Stats.incr("query.error_get_trace_combo_by_ids_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getDataTimeToLive: Future[Int] = {
    checkIfRunning
    Stats.incr("query.get_data_ttl")
    log.debug("getDataTimeToLive")

    Stats.timeFutureMillis("query.getDataTimeToLive") {
      {
        Future(storage.getDataTimeToLive)
      } rescue {
        case e: Exception =>
          log.error(e, "getDataTimeToLive failed")
          Stats.incr("query.error_get_data_ttl_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getServiceNames: Future[Set[String]] = {
    checkIfRunning
    Stats.incr("query.get_services_names")
    log.debug("getServiceNames")

    Stats.timeFutureMillis("query.getServiceNames") {
      {
        index.getServiceNames
      } rescue {
        case e: Exception =>
          log.error(e, "getServiceNames query failed")
          Stats.incr("query.error_get_services_names_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getSpanNames(service: String): Future[Set[String]] = {
    checkIfRunning
    Stats.incr("query.get_span_names")
    log.debug("getSpanNames")

    Stats.timeFutureMillis("query.getSpanNames") {
      {
        index.getSpanNames(service)
      } rescue {
        case e: Exception =>
          log.error(e, "getSpanNames query failed")
          Stats.incr("query.error_get_span_names_exception")
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def setTraceTimeToLive(traceId: Long, ttlSeconds: Int): Future[Unit] = {
    checkIfRunning
    Stats.getCounter("query.set_ttl").incr()
    log.debug("setTimeToLive: " + traceId + " " + ttlSeconds)

    Stats.timeFutureMillis("query.setTimeToLive") {
      {
        storage.setTimeToLive(traceId, ttlSeconds.seconds)
      } rescue {
        case e: Exception =>
          log.error(e, "setTimeToLive failed")
          Stats.getCounter("query.error_set_ttl_exception").incr()
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  def getTraceTimeToLive(traceId: Long): Future[Int] = {
    checkIfRunning
    Stats.getCounter("query.get_ttl").incr()
    log.debug("getTimeToLive: " + traceId)

    Stats.timeFutureMillis("query.getTimeToLive") {
      {
        storage.getTimeToLive(traceId).map(_.inSeconds)
      } rescue {
        case e: Exception =>
          log.error(e, "getTimeToLive failed")
          Stats.getCounter("query.error_get_ttl_exception").incr()
          Future.exception(gen.QueryException(e.toString))
      }
    }
  }

  private def checkIfRunning() = {
    if (!running) {
      log.warning("Server not running, throwing exception")
      throw new TException("Server not running")
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
