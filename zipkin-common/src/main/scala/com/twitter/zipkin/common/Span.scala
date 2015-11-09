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
 * A span represents one RPC request. A trace is made up of many spans.
 *
 * A span can contain multiple annotations, some are always included such as
 * Client send -> Server received -> Server send -> Client receive.
 *
 * Some are created by users, describing application specific information,
 * such as cache hits/misses.
 *
 * @param traceId random long that identifies the trace, will be set in all spans in this trace
 * @param name name of span, can be rpc method name for example, in lowercase.
 * @param id random long that identifies this span
 * @param parentId reference to the parent span in the trace tree
 * @param timestamp epoch microseconds of the start of this span. None when a partial span.
 * @param duration microseconds comprising the critical path, if known.
 * @param annotations annotations, containing a timestamp and some value. both user generated and
 * some fixed ones from the tracing framework. Sorted ascending by timestamp
 * @param binaryAnnotations  binary annotations, can contain more detailed information such as
 * serialized objects. Sorted ascending by timestamp. Sorted ascending by timestamp
 * @param debug if this is set we will make sure this span is stored, no matter what the samplers want
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
