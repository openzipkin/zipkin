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
package com.twitter.zipkin.web

import com.twitter.finagle.tracing.SpanId
import com.twitter.zipkin.common.{Trace, Endpoint, Span, SpanTreeEntry}
import com.twitter.zipkin.web.Util.getIdToSpanMap

case class SpanTimestamp(name: String, timestamp: Long, duration: Long) {
  def endTs = timestamp + duration
}

object TraceSummary {

  /**
   * Return a summary of this trace or none if we
   * cannot construct a trace summary. Could be that we have no spans.
   */
  def apply(trace: List[Span]): Option[TraceSummary] = {
    val duration = Trace.duration(trace).getOrElse(0L)
    val endpoints = trace.flatMap(_.annotations).flatMap(_.host).distinct
    for (
      traceId <- trace.headOption.map(_.traceId);
      timestamp <- trace.headOption.flatMap(_.timestamp)
    ) yield TraceSummary(
      SpanId(traceId).toString,
      timestamp,
      duration,
      spanTimestamps(trace),
      endpoints)
  }

  /**
   * Returns a map of services to a list of their durations
   */
  private def spanTimestamps(spans: List[Span]): List[SpanTimestamp] = {
    for {
      span <- spans.toList
      serviceName <- span.serviceNames
      timestamp <- span.timestamp
      duration <- span.duration
    } yield SpanTimestamp(serviceName, timestamp, duration)
  }

  /**
   * Figures out the "span depth". This is used in the ui
   * to figure out how to lay out the spans in the visualization.
   * @return span id -> depth in the tree
   */
  def toSpanDepths(spans: List[Span]): Map[Long, Int] = {
    getRootMostSpan(spans) match {
      case None => return Map.empty
      case Some(s) => {
        val spanTree = SpanTreeEntry.create(s, spans)
        spanTree.depths(1)
      }
    }
  }

  /**
   * In some cases we don't care if it's the actual root span or just the span
   * that is closes to the root. For example it could be that we don't yet log spans
   * from the root service, then we want the one just below that.
   * FIXME if there are holes in the trace this might not return the correct span
   */
  private def getRootMostSpan(spans: List[Span]): Option[Span] = {
    spans.find(!_.parentId.isDefined) orElse {
      val idSpan = getIdToSpanMap(spans)
      spans.headOption map {
        recursiveGetRootMostSpan(idSpan, _)
      }
    }
  }

  private def recursiveGetRootMostSpan(idSpan: Map[Long, Span], prevSpan: Span): Span = {
    // parent id shouldn't be none as then we would have returned already
    val span = for (id <- prevSpan.parentId; s <- idSpan.get(id)) yield
    recursiveGetRootMostSpan(idSpan, s)
    span.getOrElse(prevSpan)
  }
}

/**
 * json-friendly representation of a trace summary
 *
 * @param traceId id of this trace
 * @param timestamp when did the trace start?
 * @param duration how long did the traced operation take?
 * @param endpoints endpoints involved in the traced operation
 */
case class TraceSummary(
  traceId: String,
  timestamp: Long,
  duration: Long,
  spanTimestamps: List[SpanTimestamp],
  endpoints: List[Endpoint])
