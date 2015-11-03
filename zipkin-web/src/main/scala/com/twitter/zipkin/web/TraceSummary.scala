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
import com.twitter.zipkin.common.{Endpoint, Span, Trace}

case class SpanTimestamp(name: String, startTs: Long, endTs: Long) {
  def duration = endTs - startTs
}

object TraceSummary {

  /**
   * Return a summary of this trace or none if we
   * cannot construct a trace summary. Could be that we have no spans.
   */
  def apply(t: Trace): Option[TraceSummary] = {

    val endpoints = t.spans.flatMap(_.annotations).flatMap(_.host).distinct
    for (
      traceId <- t.spans.headOption.map(_.traceId);
      startTs <- t.spans.headOption.flatMap(_.startTs)
    ) yield TraceSummary(
      SpanId(traceId).toString,
      startTs,
      startTs + t.duration,
      t.duration,
      spanTimestamps(t.spans),
      endpoints)
  }

  /**
   * Returns a map of services to a list of their durations
   */
  private def spanTimestamps(spans: Seq[Span]): List[SpanTimestamp] = {
    for {
      span <- spans.toList
      serviceName <- span.serviceNames
      first <- span.firstAnnotation
      last <- span.lastAnnotation
    } yield SpanTimestamp(serviceName, first.timestamp, last.timestamp)
  }

  /**
   * Figures out the "span depth". This is used in the ui
   * to figure out how to lay out the spans in the visualization.
   * @return span id -> depth in the tree
   */
  def toSpanDepths(t: Trace): Map[Long, Int] = {
    getRootMostSpan(t) match {
      case None => return Map.empty
      case Some(s) => {
        val spanTree = t.getSpanTree(s, t.getIdToChildrenMap)
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
  private def getRootMostSpan(t: Trace): Option[Span] = {
    t.getRootSpan orElse {
      val idSpan = t.getIdToSpanMap
      t.spans.headOption map { recursiveGetRootMostSpan(idSpan, _) }
    }
  }

  private def recursiveGetRootMostSpan(idSpan: Map[Long, Span], prevSpan: Span): Span = {
    // parent id shouldn't be none as then we would have returned already
    val span = for ( id <- prevSpan.parentId; s <- idSpan.get(id) ) yield
    recursiveGetRootMostSpan(idSpan, s)
    span.getOrElse(prevSpan)
  }
}

/**
 * json-friendly representation of a trace summary
 *
 * @param traceId id of this trace
 * @param startTs when did the trace start?
 * @param endTs when did the trace end?
 * @param durationMicro how long did the traced operation take?
 * @param endpoints endpoints involved in the traced operation
 */
case class TraceSummary(
  traceId: String,
  startTs: Long,
  endTs: Long,
  durationMicro: Long,
  spanTimestamps: List[SpanTimestamp],
  endpoints: List[Endpoint])
