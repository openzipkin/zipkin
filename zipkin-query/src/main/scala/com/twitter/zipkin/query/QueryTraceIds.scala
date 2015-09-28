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

import com.google.common.base.Charsets.UTF_8
import com.twitter.util.Future
import com.twitter.zipkin.query.constants._
import com.twitter.zipkin.storage._
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.thriftscala.QueryRequest
import java.nio.ByteBuffer
import javax.inject.Inject

// TODO: this class has a lot of tech debt, and also hints spanstore needs to be redone to not require a preparatory id fetch.
class QueryTraceIds @Inject()(spanStore: SpanStore) extends ((thriftscala.QueryRequest) => Future[Seq[Long]]) {

  override def apply(qr: QueryRequest) = {
    val sliceQueries = Seq[Option[Seq[SliceQuery]]](
      qr.spanName.map { n => Seq(SpanSliceQuery(n)) },
      qr.annotations.map { _.map { AnnotationSliceQuery(_, None) } },
      qr.binaryAnnotations.map { _.map { e => AnnotationSliceQuery(e._1, Some(ByteBuffer.wrap(e._2.getBytes(UTF_8)))) }(collection.breakOut) }
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
            queryResponse(traceIdsIntersect(ids), qr)
          }
        }
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
    qr: thriftscala.QueryRequest
  ): Future[Seq[Long]] = {
    Future.value(ids.slice(0, qr.limit).map(_.traceId))
  }

  private trait SliceQuery
  private case class SpanSliceQuery(name: String) extends SliceQuery
  private case class AnnotationSliceQuery(key: String, value: Option[ByteBuffer]) extends SliceQuery

  private[this] def querySlices(slices: Seq[SliceQuery], qr: thriftscala.QueryRequest): Future[Seq[Seq[IndexedTraceId]]] =
    Future.collect(slices map {
      case SpanSliceQuery(name) =>
        spanStore.getTraceIdsByName(qr.serviceName, Some(name), qr.endTs, qr.limit)
      case AnnotationSliceQuery(key, value) =>
        spanStore.getTraceIdsByAnnotation(qr.serviceName, key, value, qr.endTs, qr.limit)
      case s =>
        Future.exception(new Exception("Uknown SliceQuery: %s".format(s)))
    })
}
