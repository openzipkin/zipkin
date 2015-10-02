package com.twitter.zipkin.storage

import com.twitter.util.Time

/**
 * @param serviceName Mandatory [[com.twitter.zipkin.common.Endpoint.serviceName]]
 * @param spanName When present, only include traces with this [[com.twitter.zipkin.common.Span.name]]
 * @param annotations Include  traces whose [[com.twitter.zipkin.common.Span.annotations]] value is in this list.
 *                    This is an OR condition against the set, as well against [[binaryAnnotations]]
 * @param binaryAnnotations Include traces whose [[com.twitter.zipkin.common.Span.binaryAnnotations]] include a
 *                          String whose key and value are an entry in this map.
 *                          This is an OR condition against the set, as well against [[annotations]]
 * @param endTs only return traces where all [[com.twitter.zipkin.common.Span.lastTimestamp]] are at
 *              or before this time in epoch microseconds. Defaults to current time.
 * @param limit maximum number of traces to return. Defaults to 10
 */
case class QueryRequest(serviceName: String,
                        spanName: Option[String] = None,
                        annotations: Set[String] = Set.empty,
                        binaryAnnotations: Set[(String, String)] = Set.empty,
                        endTs: Long = Time.now.inMicroseconds,
                        limit: Int = 10)