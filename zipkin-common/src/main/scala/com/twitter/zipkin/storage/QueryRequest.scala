package com.twitter.zipkin.storage

import com.twitter.util.Time
import com.twitter.zipkin.util.Util.checkArgument
import scala.util.hashing.MurmurHash3

/**
 * @param _serviceName Mandatory [[com.twitter.zipkin.common.Endpoint.serviceName]]
 * @param _spanName When present, only include traces with this [[com.twitter.zipkin.common.Span.name]]
 * @param annotations Include traces whose [[com.twitter.zipkin.common.Span.annotations]] include a value in this set.
 *                    This is an AND condition against the set, as well against [[binaryAnnotations]]
 * @param binaryAnnotations Include traces whose [[com.twitter.zipkin.common.Span.binaryAnnotations]] include a
 *                          String whose key and value are an entry in this set.
 *                          This is an AND condition against the set, as well against [[annotations]]
 * @param endTs only return traces where all [[com.twitter.zipkin.common.Span.endTs]] are at
 *              or before this time in epoch microseconds. Defaults to current time.
 * @param limit maximum number of traces to return. Defaults to 10
 */
// This is not a case-class as we need to enforce serviceName and spanName as lowercase
class QueryRequest(_serviceName: String,
                   _spanName: Option[String] = None,
                   val annotations: Set[String] = Set.empty,
                   val binaryAnnotations: Set[(String, String)] = Set.empty,
                   val endTs: Long = Time.now.inMicroseconds,
                   val limit: Int = 10) {

  /** Mandatory [[com.twitter.zipkin.common.Endpoint.serviceName]] */
  val serviceName: String = _serviceName.toLowerCase

  /** When present, only include traces with this [[com.twitter.zipkin.common.Span.name]] */
  val spanName: Option[String] = _spanName.map(_.toLowerCase)

  checkArgument(serviceName.nonEmpty, "serviceName was empty")
  checkArgument(spanName.map(_.nonEmpty).getOrElse(true), "spanName was empty")
  checkArgument(endTs > 0, () => "endTs should be positive, in epoch microseconds: was %d".format(endTs))
  checkArgument(limit > 0, () => "limit should be positive: was %d".format(limit))

  override lazy val hashCode =
    MurmurHash3.seqHash(List(serviceName, spanName, annotations, binaryAnnotations, endTs, limit))

  override def equals(other: Any) = other match {
    case x: QueryRequest =>
      x.serviceName == serviceName && x.spanName == spanName && x.annotations == annotations &&
        x.binaryAnnotations == binaryAnnotations && x.endTs == endTs && x.limit == limit
    case _ => false
  }

  def copy(
    serviceName: String = this.serviceName,
    spanName: Option[String] = this.spanName,
    annotations: Set[String] = this.annotations,
    binaryAnnotations: Set[(String, String)] = this.binaryAnnotations,
    endTs: Long = this.endTs,
    limit: Int = this.limit
  ) = QueryRequest(serviceName, spanName, annotations, binaryAnnotations, endTs, limit)
}

object QueryRequest {
  def apply(
    serviceName: String,
    spanName: Option[String] = None,
    annotations: Set[String] = Set.empty,
    binaryAnnotations: Set[(String, String)] = Set.empty,
    endTs: Long = Time.now.inMicroseconds,
    limit: Int = 10
  ) = new QueryRequest(serviceName, spanName, annotations, binaryAnnotations, endTs, limit)
}
