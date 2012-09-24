package com.twitter.zipkin.storage.cassandra

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
import com.twitter.cassie._
import scala.collection.JavaConverters._
import com.twitter.ostrich.stats.Stats
import collection.Set
import java.nio.ByteBuffer
import java.util.{Map => JMap}
import com.twitter.zipkin.common.{Annotation, Span}
import com.twitter.zipkin.util.Util
import com.twitter.zipkin.storage.{IndexedTraceId, TraceIdDuration, Index}
import com.twitter.util.Future
import com.twitter.zipkin.config.CassandraConfig
import com.twitter.zipkin.Constants

/**
 * An index for the spans and traces using Cassandra with the Cassie client.
 */
trait CassandraIndex extends Index with Cassandra {

  val config: CassandraConfig

  /* Index `ColumnFamily`s */
  val serviceSpanNameIndex : ColumnFamily[String, Long, Long]
  val serviceNameIndex     : ColumnFamily[String, Long, Long]
  val annotationsIndex     : ColumnFamily[ByteBuffer, Long, Long]
  val durationIndex        : ColumnFamily[Long, Long, String]
  val serviceNames         : ColumnFamily[String, String, String]
  val spanNames            : ColumnFamily[String, String, String]

  // store the span name used in this service
  private val CASSANDRA_STORE_SPAN_NAME = Stats.getCounter("cassandra_storespanname")
  private val CASSANDRA_STORE_SPAN_NAME_NO_SPAN_NAME = Stats.getCounter("cassandra_storespanname_nospanname")
  // store the service names
  private val CASSANDRA_STORE_SERVICE_NAME = Stats.getCounter("cassandra_storeservicename")
  private val CASSANDRA_STORE_SERVICE_NAME_NO_SERVICE_NAME = Stats.getCounter("cassandra_storeservicename_noservicename")
  // index the span by service name and span name
  private val CASSANDRA_INDEX_SPAN_BY_NAMES = Stats.getCounter("cassandra_indexspanbynames")
  // no annotations on the span being indexed
  private val CASSANDRA_INDEX_SPAN_BY_NAME_NO_LAST_ANNOTATION = Stats.getCounter("cassandra_indexspanbynames_nolastannotation")
  // index the span by annotations (both time and kv based)
  private val CASSANDRA_INDEX_SPAN_BY_ANNOTATIONS = Stats.getCounter("cassandra_indexspanbyannotations")
  // no annotations on the span being indexed
  private val CASSANDRA_INDEX_SPAN_BY_ANNOTATIONS_NO_LAST_ANNOTATION = Stats.getCounter("cassandra_indexspanbyannotations_nolastannotation")

  // find trace ids in the index by name
  private val CASSANDRA_GET_TRACE_IDS_BY_NAME = Stats.getCounter("cassandra_gettraceidsbyname")
  // find trace ids by annotation in the index
  private val CASSANDRA_GET_TRACE_IDS_BY_ANN = Stats.getCounter("cassandra_gettraceidsbyannotation")
  // get service names
  private val CASSANDRA_GET_SERVICE_NAMES = Stats.getCounter("cassandra_getservicenames")
  // get span names for a service
  private val CASSANDRA_GET_SPAN_NAMES = Stats.getCounter("cassandra_getspannames")

  private val WRITE_REQUEST_COUNTER = Stats.getCounter("cassandra.write_request_counter")

  private val SERVICE_NAMES_KEY = "servicenames"

  // used to delimit the key value annotation parts in the index
  private val INDEX_DELIMITER = ":"

  Stats.addGauge("cassandra_ttl_days") { config.tracesTimeToLive.inDays }

  private def encode(serviceName: String, index: String) = {
    Array(serviceName, index).mkString(INDEX_DELIMITER)
  }

  def getServiceNames: Future[Set[String]] = {
    CASSANDRA_GET_SERVICE_NAMES.incr
    serviceNames.getRow(SERVICE_NAMES_KEY).map(_.values.asScala.map(v => v.name).toSet)
  }

  def getSpanNames(service: String): Future[Set[String]] = {
    CASSANDRA_GET_SPAN_NAMES.incr
    spanNames.getRow(service).map(_.values.asScala.map(v => v.name).toSet)
  }

  /*
   * Storage write methods
   * ---------------------
   */

  def indexServiceName(span: Span) : Future[Unit] = {
    CASSANDRA_STORE_SERVICE_NAME.incr
    Future.join {
      span.serviceNames.map {
        _ match {
          case "" =>
            CASSANDRA_STORE_SERVICE_NAME_NO_SERVICE_NAME.incr()
            Future.Unit
          case s @ _ =>
            WRITE_REQUEST_COUNTER.incr()
            val serviceNameCol = Column[String, String](s.toLowerCase, "").ttl(config.tracesTimeToLive)
            serviceNames.insert(SERVICE_NAMES_KEY, serviceNameCol)
        }
      }.toSeq
    }
  }

  def indexSpanNameByService(span: Span) : Future[Unit]  = {
    CASSANDRA_STORE_SPAN_NAME.incr
    if (span.name == "") {
      CASSANDRA_STORE_SPAN_NAME_NO_SPAN_NAME.incr()
      Future.Unit
    } else {
      val spanNameCol = Column[String, String](span.name.toLowerCase, "").ttl(config.tracesTimeToLive)
      Future.join {
        span.serviceNames.map {
          WRITE_REQUEST_COUNTER.incr()
          spanNames.insert(_, spanNameCol)
        }.toSeq
      }
    }
  }

  /*
   * Index read methods
   * ------------------
   */

  def getTraceIdsByName(serviceName: String, spanName: Option[String],
                        endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    CASSANDRA_GET_TRACE_IDS_BY_NAME.incr
    // if we have a span name, look up in the service + span name index
    // if not, look up by service name only
    val row = spanName match {
      case Some(span) =>
        val key = serviceName.toLowerCase + "." + span.toLowerCase
        serviceSpanNameIndex.getRowSlice(key, Some(endTs), None, limit, Order.Reversed)
      case None =>
        val key = serviceName.toLowerCase
        serviceNameIndex.getRowSlice(key, Some(endTs), None, limit, Order.Reversed)
    }

    // Future[Seq[Column[Long, Long]]] => Future[Seq[IndexedTraceId]]
    row map {
      _.map { column =>
        IndexedTraceId(traceId = column.value, timestamp = column.name)
      }
    }
  }


  def getTraceIdsByAnnotation(service: String, annotation: String, value: Option[ByteBuffer],
                              endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    CASSANDRA_GET_TRACE_IDS_BY_ANN.incr
    val row = value match {
      case Some(v) => {
        val key = ByteBuffer.wrap(encode(service, annotation).getBytes ++ INDEX_DELIMITER.getBytes ++ Util.getArrayFromBuffer(v))
        annotationsIndex.getRowSlice(key, Some(endTs), None, limit, Order.Reversed)
      }
      case None =>
        val key = ByteBuffer.wrap(encode(service, annotation).getBytes)
        annotationsIndex.getRowSlice(key, Some(endTs), None, limit, Order.Reversed)
    }

    row map {
      _.map { column =>
        IndexedTraceId(traceId = column.value, timestamp = column.name)
      }
    }
  }

  case class TraceIdTimestamp(traceId: Long, timestamp: Option[Long])

  /**
   * Fetch the duration or an estimate thereof from the traces.
   */

  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
    val startRows = durationIndex.multigetRows(traceIds.toSet.asJava, None, None, Order.Normal, 1)
    val traceStartTimestamp = getTraceIdTimestamp(startRows)

    val endRows = durationIndex.multigetRows(traceIds.toSet.asJava, None, None, Order.Reversed, 1)
    val traceEndTimestamp = getTraceIdTimestamp(endRows)

    traceStartTimestamp.join(traceEndTimestamp).map { case (start, end) =>
      start.zip(end).collect {
        case (TraceIdTimestamp(startId, Some(startTs)), TraceIdTimestamp(endId, Some(endTs))) if (startId == endId) =>
          TraceIdDuration(endId, endTs - startTs, startTs)
      }.toSeq
    }
  }


  private def getTraceIdTimestamp(rowsFuture: Future[JMap[Long, JMap[Long, Column[Long, String]]]]):
      Future[Iterable[TraceIdTimestamp]] = {

    rowsFuture.map { rows =>
      rows.asScala.map { case (ts, cols) =>
        // should only be one returned from cassandra
        TraceIdTimestamp(ts, cols.entrySet().asScala.headOption.map(_.getKey))
      }
    }
  }

  /*
   * Index write methods
   * -------------------
   */
  def indexTraceIdByServiceAndName(span: Span) : Future[Unit] = {
    CASSANDRA_INDEX_SPAN_BY_NAMES.incr
    val lastAnnotation = span.lastAnnotation getOrElse {
      CASSANDRA_INDEX_SPAN_BY_NAME_NO_LAST_ANNOTATION.incr
      return Future.Unit
    }
    val timestamp = lastAnnotation.timestamp
    val serviceNames = span.serviceNames

    val futures = serviceNames.map(serviceName => {
      WRITE_REQUEST_COUNTER.incr()
      val serviceSpanIndexKey = serviceName + "." + span.name.toLowerCase
      val serviceSpanIndexCol = Column[Long, Long](timestamp, span.traceId).ttl(config.tracesTimeToLive)
      val serviceSpanNameFuture = serviceSpanNameIndex.insert(serviceSpanIndexKey, serviceSpanIndexCol)

      WRITE_REQUEST_COUNTER.incr()
      val serviceIndexCol = Column[Long, Long](timestamp, span.traceId).ttl(config.tracesTimeToLive)
      val serviceNameFuture = serviceNameIndex.insert(serviceName, serviceIndexCol)
      List(serviceSpanNameFuture, serviceNameFuture)
    }).toList.flatten
    Future.join(futures)
  }

  def indexSpanByAnnotations(span: Span) : Future[Unit] = {
    CASSANDRA_INDEX_SPAN_BY_ANNOTATIONS.incr
    val lastAnnotation = span.lastAnnotation getOrElse {
      CASSANDRA_INDEX_SPAN_BY_ANNOTATIONS_NO_LAST_ANNOTATION.incr
      return Future.Unit
    }
    val timestamp = lastAnnotation.timestamp

    val batch = annotationsIndex.batch

    span.annotations.filter { a =>
      // skip core annotations since that query can be done by service name/span name anyway
      !Constants.CoreAnnotations.contains(a.value)
    } groupBy {
      _.value
    } foreach { m: (String, List[Annotation]) =>
      val a = m._2.min
      a.host match {
        case Some(endpoint) => {
          WRITE_REQUEST_COUNTER.incr()
          val col = Column[Long, Long](a.timestamp, span.traceId).ttl(config.tracesTimeToLive)
          batch.insert(ByteBuffer.wrap(encode(endpoint.serviceName, a.value).getBytes), col)
        }
        case None => // Nothin
      }
    }

    span.binaryAnnotations foreach { ba =>
      ba.host match {
        case Some(endpoint) => {
          WRITE_REQUEST_COUNTER.incr(2)
          val key = encode(endpoint.serviceName, ba.key).getBytes
          val col = Column[Long, Long](timestamp, span.traceId).ttl(config.tracesTimeToLive)
          batch.insert(ByteBuffer.wrap(key ++ INDEX_DELIMITER.getBytes ++ Util.getArrayFromBuffer(ba.value)), col)
          batch.insert(ByteBuffer.wrap(key), col)
        }
        case None =>
      }
    }
    val annFuture = batch.execute()

    annFuture.unit
  }

  def indexSpanDuration(span: Span): Future[Void] = {
    val first = span.firstAnnotation.map(_.timestamp)
    val last = span.lastAnnotation.map(_.timestamp)

    val batch = durationIndex.batch()
    first foreach {
      WRITE_REQUEST_COUNTER.incr()
      t => batch.insert(span.traceId, Column[Long, String](t, "").ttl(config.tracesTimeToLive))
    }
    last foreach {
      WRITE_REQUEST_COUNTER.incr()
      t => batch.insert(span.traceId, Column[Long, String](t, "").ttl(config.tracesTimeToLive))
    }
    batch.execute()
  }
}
