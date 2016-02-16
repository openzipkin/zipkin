package com.twitter.zipkin.storage

import com.twitter.util.Time
import com.twitter.zipkin.util.Util.checkArgument
import scala.util.hashing.MurmurHash3

/**
 * Invoking this request retrieves traces matching the below filters.
 *
 * <p/> Results should be filtered against [[endTs]], subject to [[limit]] and [[_lookback]]. For
 * example, if endTs is 10:20 today, limit is 10, and lookback is 7 days, traces returned should be
 * those nearest to 10:20 today, not 10:20 a week ago.
 *
 * <p/> Time units of [[endTs]] and [[_lookback]] are milliseconds as opposed to microseconds, which
 * the grain of [[com.twitter.zipkin.common.Span.timestamp]]. Milliseconds is a more familiar and
 * supported granularity for query, index and windowing functions.
 *
 * @param _serviceName Mandatory [[com.twitter.zipkin.common.Endpoint.serviceName]] and constrains
 *                     all other parameters.
 * @param _spanName When present, only include traces with this [[com.twitter.zipkin.common.Span.name]]
 * @param annotations Include traces whose [[com.twitter.zipkin.common.Span.annotations]] include a value in this set.
 *                    This is an AND condition against the set, as well against [[binaryAnnotations]]
 * @param binaryAnnotations Include traces whose [[com.twitter.zipkin.common.Span.binaryAnnotations]] include a
 *                          String whose key and value are an entry in this set.
 *                          This is an AND condition against the set, as well against [[annotations]]
 * @param minDuration only return traces whose [[com.twitter.zipkin.common.Span.duration]] is
 *                    greater than or equal to minDuration microseconds.
 * @param maxDuration only return traces whose [[com.twitter.zipkin.common.Span.duration]] is less
 *                    than or equal to maxDuration microseconds. Only valid with [[minDuration]].
 * @param endTs only return traces where all [[com.twitter.zipkin.common.Span.timestamp]] are at
 *              or before this time in epoch milliseconds. Defaults to current time.
 * @param _lookback only return traces where all [[com.twitter.zipkin.common.Span.timestamp]] are at
 *                  or after (endTs - lookback) in milliseconds. Defaults to endTs.
 * @param limit maximum number of traces to return. Defaults to 10.
 */
// This is not a case-class as we need to enforce serviceName and spanName as lowercase
class QueryRequest(_serviceName: String,
                   _spanName: Option[String] = None,
                   val annotations: Set[String] = Set.empty,
                   val binaryAnnotations: Set[(String, String)] = Set.empty,
                   val minDuration: Option[Long] = None,
                   val maxDuration: Option[Long] = None,
                   val endTs: Long = Time.now.inMillis,
                   _lookback: Option[Long] = None,
                   val limit: Int = 10) {

  /** Mandatory [[com.twitter.zipkin.common.Endpoint.serviceName]] */
  val serviceName: String = _serviceName.toLowerCase

  /** When present, only include traces with this [[com.twitter.zipkin.common.Span.name]] */
  val spanName: Option[String] = _spanName.map(_.toLowerCase)

  /**
   * Only return traces where all [[com.twitter.zipkin.common.Span.timestamp]] are at
   * or after (endTs - lookback) in milliseconds.
   */
  val lookback: Long = Math.min(_lookback.getOrElse(endTs), endTs)

  checkArgument(serviceName.nonEmpty, "serviceName was empty")
  checkArgument(spanName.map(_.nonEmpty).getOrElse(true), "spanName was empty")
  checkArgument(minDuration.map(_ > 0).getOrElse(true),
    () => "minDuration should be positive: was " + minDuration.get)
  checkArgument(maxDuration.map(_ > 0).getOrElse(true),
    () => "maxDuration should be positive: was " + maxDuration.get)
  checkArgument(maxDuration.map(_ => minDuration.isDefined).getOrElse(true),
    "minDuration is required when specifying maxDuration")
  checkArgument(endTs > 0, () => "endTs should be positive, in epoch milliseconds: was " + endTs)
  checkArgument(lookback > 0, () => "lookback should be positive, in milliseconds: was " + lookback)
  checkArgument(limit > 0, () => "limit should be positive: was " + limit)

  override lazy val hashCode =
    MurmurHash3.seqHash(List(serviceName, spanName, annotations, binaryAnnotations, minDuration, maxDuration, endTs, lookback, limit))

  override def equals(other: Any) = other match {
    case x: QueryRequest =>
      x.serviceName == serviceName && x.spanName == spanName && x.annotations == annotations &&
        x.binaryAnnotations == binaryAnnotations && x.minDuration == minDuration &&
        x.maxDuration == maxDuration && x.endTs == endTs && x.lookback == lookback && x.limit == limit
    case _ => false
  }

  def copy(
    serviceName: String = this.serviceName,
    spanName: Option[String] = this.spanName,
    annotations: Set[String] = this.annotations,
    binaryAnnotations: Set[(String, String)] = this.binaryAnnotations,
    minDuration: Option[Long] = this.minDuration,
    maxDuration: Option[Long] = this.maxDuration,
    endTs: Long = this.endTs,
    lookback: Option[Long] = Some(this.lookback),
    limit: Int = this.limit
  ) = QueryRequest(serviceName, spanName, annotations, binaryAnnotations, minDuration, maxDuration, endTs, lookback, limit)
}

object QueryRequest {
  def apply(
    serviceName: String,
    spanName: Option[String] = None,
    annotations: Set[String] = Set.empty,
    binaryAnnotations: Set[(String, String)] = Set.empty,
    minDuration: Option[Long] = None,
    maxDuration: Option[Long] = None,
    endTs: Long = Time.now.inMillis,
    lookback: Option[Long] = None,
    limit: Int = 10
  ) = new QueryRequest(serviceName, spanName, annotations, binaryAnnotations, minDuration, maxDuration, endTs, lookback,limit)
}
