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
package com.twitter.zipkin.storage

import com.twitter.zipkin.common.Span
import com.twitter.util.Future
import scala.collection.Set
import java.nio.ByteBuffer

/**
 * Duration of the trace in question in microseconds.
 */
case class TraceIdDuration(traceId: Long, duration: Long, startTimestamp: Long)

/* A trace ID and its associated timestamp */
case class IndexedTraceId(traceId: Long, timestamp: Long)

trait Index {

  /**
   * Close the index
   */
  def close()

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByName(serviceName: String, spanName: Option[String],
                        endTs: Long, limit: Int): Future[Seq[IndexedTraceId]]

  /**
   * Get the trace ids for this annotation between the two timestamps. If value is also passed we expect
   * both the annotation key and value to be present in index for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
                              endTs: Long, limit: Int): Future[Seq[IndexedTraceId]]

  /**
   * Fetch the duration or an estimate thereof from the traces.
   * Duration returned in micro seconds.
   */
  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]]

  /**
   * Get all the service names for as far back as the ttl allows.
   */
  def getServiceNames: Future[Set[String]]

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   */
  def getSpanNames(service: String): Future[Set[String]]


  /**
   * Index a trace id on the service and name of a specific Span
   */
  def indexTraceIdByServiceAndName(span: Span) : Future[Unit]

  /**
   * Index the span by the annotations attached
   */
  def indexSpanByAnnotations(span: Span) : Future[Unit]

  /**
   * Store the service name, so that we easily can
   * find out which services have been called from now and back to the ttl
   */
  def indexServiceName(span: Span) : Future[Unit]

  /**
   * Index the span name on the service name. This is so we
   * can get a list of span names when given a service name.
   * Mainly for UI purposes
   */
  def indexSpanNameByService(span: Span) : Future[Unit]

  /**
   * Index a span's duration. This is so we can look up the trace duration.
   */
  def indexSpanDuration(span: Span): Future[Void]
}
