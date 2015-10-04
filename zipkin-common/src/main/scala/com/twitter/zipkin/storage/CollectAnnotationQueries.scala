package com.twitter.zipkin.storage

import com.google.common.base.Charsets._
import com.twitter.util.Future
import com.twitter.zipkin.Constants._
import com.twitter.zipkin.common.Span
import java.nio.ByteBuffer

/**
 * Convenience trait to until existing [[SpanStore]] implementations implement
 * [[QueryRequest]] natively. This will be inefficient in storage systems that
 * can combine multiple conditions (annotations) into the same select.
 */
@deprecated(message = "Implement SpanStore.getTraces() directly", since = "1.15.0")
trait CollectAnnotationQueries {

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  protected def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]]

  /**
   * Get the trace ids for this annotation between the two timestamps. If value is also passed we expect
   * both the annotation key and value to be present in index for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  protected def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]]

  /** @see [[com.twitter.zipkin.storage.SpanStore.getTracesByIds()]] */
  def getTracesByIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]]

  /** @see [[com.twitter.zipkin.storage.SpanStore.getTraces()]] */
  def getTraces(qr: QueryRequest): Future[Seq[Seq[Span]]] = {
    val sliceQueries = Seq[Set[SliceQuery]](
      qr.spanName.map(SpanSliceQuery(_)).toSet,
      qr.annotations.map(AnnotationSliceQuery(_, None)),
      qr.binaryAnnotations.map(e => AnnotationSliceQuery(e._1, Some(ByteBuffer.wrap(e._2.getBytes(UTF_8)))))
    ).flatten

    val ids = sliceQueries match {
      case Nil =>
        getTraceIdsByName(qr.serviceName, None, qr.endTs, qr.limit).flatMap(queryResponse(_, qr))

      case slice :: Nil =>
        querySlices(sliceQueries, qr).flatMap(ids => queryResponse(ids.flatten, qr))

      case _ =>
        // TODO: timestamps endTs is the wrong name for all this
        querySlices(sliceQueries, qr.copy(limit = 1)) flatMap { ids =>
          val ts = padTimestamp(ids.flatMap(_.map(_.timestamp)).reduceOption(_ min _).getOrElse(0))
          querySlices(sliceQueries, qr.copy(endTs = ts)) flatMap { ids =>
            queryResponse(traceIdsIntersect(ids), qr)
          }
        }
    }
    // only issue a query if trace ids were found
    ids.flatMap(ids => if (ids.isEmpty) Future.value(Seq.empty) else getTracesByIds(ids))
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
    commonTraceIds.map(id => IndexedTraceId(id, idMaps.flatMap(_(id).map(_.timestamp)).max))
  }

  private[this] def queryResponse(ids: Seq[IndexedTraceId], qr: QueryRequest): Future[Seq[Long]] = {
    Future.value(ids.filter(_.timestamp <= qr.endTs).slice(0, qr.limit).map(_.traceId))
  }

  private trait SliceQuery
  private case class SpanSliceQuery(name: String) extends SliceQuery
  private case class AnnotationSliceQuery(key: String, value: Option[ByteBuffer]) extends SliceQuery

  private[this] def querySlices(slices: Seq[SliceQuery], qr: QueryRequest): Future[Seq[Seq[IndexedTraceId]]] =
    Future.collect(slices map {
      case SpanSliceQuery(name) =>
        getTraceIdsByName(qr.serviceName, Some(name), qr.endTs, qr.limit)
      case AnnotationSliceQuery(key, value) =>
        getTraceIdsByAnnotation(qr.serviceName, key, value, qr.endTs, qr.limit)
      case s =>
        Future.exception(new Exception("Uknown SliceQuery: %s".format(s)))
    })
}
