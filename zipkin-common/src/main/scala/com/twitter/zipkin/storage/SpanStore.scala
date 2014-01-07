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

import com.twitter.finagle.{Filter, Service}
import com.twitter.util.{Closable, CloseAwaitably, Duration, Future, Time}
import com.twitter.zipkin.common.Span
import java.nio.ByteBuffer

trait SpanStoreFilter extends Filter[Seq[Span], Unit, Seq[Span], Unit]

trait SpanStore extends WriteSpanStore with ReadSpanStore

/**
 * Write store extends CloseAwaitably so we can close writes and await possible draining
 * of internal queues.
 */
trait WriteSpanStore
  extends (Seq[Span] => Future[Unit])
  with Closable
  with CloseAwaitably
{
  // Used for pinning
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit]
}

trait ReadSpanStore {
  def getTimeToLive(traceId: Long): Future[Duration]

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]]

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   *
   * The return list will contain only spans that have been found, thus
   * the return list may not match the provided list of ids.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]]
  def getSpansByTraceId(traceId: Long): Future[Seq[Span]]

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByName(
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
  def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]]

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
}
