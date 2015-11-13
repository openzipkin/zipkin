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
package com.twitter.zipkin.common

import com.twitter.zipkin.Constants
import com.twitter.zipkin.util.Util._

/**
 * A trace is a series of spans (often RPC calls) which form a latency tree.
 *
 * <p/>Spans are usually created by instrumentation in RPC clients or servers, but can also
 * represent in-process activity. Annotations in spans are similar to log statements, and are
 * sometimes created directly by application developers to indicate events of interest, such as a
 * cache miss.
 *
 * <p/>The root span is where [[traceId]] = [[id]] and [[parentId]] is empty. The root span is
 * usually the longest interval in the trace, starting with [[timestamp]] and ending with
 * [[timestamp]] + [[duration]].
 *
 * <p/>Span identifiers are packed into longs, but should be treated opaquely. String encoding is
 * fixed-width lower-hex, to avoid signed interpretation.
 *
 * @param traceId unique 8-byte identifier for a trace, set on all spans within it.
 * @param name span name in lowercase, rpc method for example. Conventionally, when the span name
 *             isn't known, name = "unknown".
 * @param id unique 8-byte identifier of this span within a trace. If this is the root span,
 *           [[id]] = [[traceId]] and [[parentId]] is absent. A span is uniquely identified in
 *           storage by (trace_id, id).
 * @param parentId the parent's [[id]]; absent if this the root span in a trace.
 * @param timestamp epoch microseconds of the start of this span; absent if this an incomplete span.
 * @param duration measurement in microseconds of the critical path, if known.
 * @param annotations associates events that explain latency with a timestamp. Unlike log
 *                    statements, annotations are often codes: for example [[Constants.ServerRecv]].
 *                    Annotations are sorted ascending by timestamp.
 * @param binaryAnnotations tags a span with context, usually to support query or aggregation. For
 *                          example, a binary annotation key could be "http.uri".
 * @param debug true is a request to store this span even if it overrides sampling policy.
 */
case class Span(
  traceId: Long,
  name: String,
  id: Long,
  parentId: Option[Long] = None,
  timestamp: Option[Long] = None,
  duration: Option[Long] = None,
  annotations: List[Annotation] = List.empty,
  binaryAnnotations: Seq[BinaryAnnotation] = Seq.empty,
  debug: Option[Boolean] = None) extends Ordered[Span] {

  checkArgument(name.toLowerCase == name, s"name must be lowercase: $name")

  override def compare(that: Span) =
    java.lang.Long.compare(timestamp.getOrElse(0L), that.timestamp.getOrElse(0L))

  def endpoints: Set[Endpoint] =
    (annotations.flatMap(_.host) ++ binaryAnnotations.flatMap(_.host)).toSet

  def serviceNames: Set[String] = endpoints.map(_.serviceName)

  /**
   * Tries to extract the best name of the service in this span. This depends on annotations
   * logged and prioritized names logged by the server over those logged by the client.
   */
  lazy val serviceName: Option[String] = {
    // Most authoritative is the label of the server's endpoint
    binaryAnnotations.find(_.key == Constants.ServerAddr).map(_.serviceName) orElse
      // Next, the label of any server annotation, logged by an instrumented server
      serverSideAnnotations.headOption.map(_.serviceName) orElse
      // Next is the label of the client's endpoint
      binaryAnnotations.find(_.key == Constants.ClientAddr).map(_.serviceName) orElse
      // Next is the label of any client annotation, logged by an instrumented client
      clientSideAnnotations.headOption.map(_.serviceName) orElse
      // Finally is the label of the local component's endpoint
      binaryAnnotations.find(_.key == Constants.LocalComponent).map(_.serviceName)
  }

  /**
   * Take two spans with the same span id and merge all data into one of them.
   */
  def merge(mergeFrom: Span): Span = {
    if (id != mergeFrom.id) {
      throw new IllegalArgumentException("Span ids must match")
    }

    // ruby tracing can give us an empty name in one part of the span
    val selectedName = name match {
      case "" => mergeFrom.name
      case "unknown" => mergeFrom.name
      case _ => name
    }

    val selectedTimestamp = Seq(timestamp, mergeFrom.timestamp).flatten.reduceOption(_ min _)
    val selectedDuration = Trace.duration(List(this, mergeFrom))
                                .orElse(duration).orElse(mergeFrom.duration)

    new Span(
      traceId,
      selectedName,
      id,
      parentId,
      selectedTimestamp,
      selectedDuration,
      (annotations ++ mergeFrom.annotations).sorted,
      binaryAnnotations ++ mergeFrom.binaryAnnotations,
      if (debug.getOrElse(false) | mergeFrom.debug.getOrElse(false)) Some(true) else None
    )
  }

  /**
   * Pick out the core client side annotations
   */
  def clientSideAnnotations: Seq[Annotation] =
    annotations.filter(a => Constants.CoreClient.contains(a.value))

  /**
   * Pick out the core server side annotations
   */
  def serverSideAnnotations: Seq[Annotation] =
    annotations.filter(a => Constants.CoreServer.contains(a.value))
}
