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
import scala.collection.mutable

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
  annotations: List[Annotation] = List.empty,
  binaryAnnotations: Seq[BinaryAnnotation] = Seq.empty,
  debug: Option[Boolean] = None) extends Ordered[Span] {

  checkArgument(name.toLowerCase == name, s"name must be lowercase: $name")

  lazy val timestamp: Option[Long] = annotations.headOption.map(_.timestamp)

  /**
   * Duration in microseconds.
   *
   * Absent when this is span has only binary annotations or only a single
   * annotation. This is possible when a span isn't complete, or messages that
   * complete it were lost.
   */
  def duration: Option[Long] =
    for (first <- annotations.headOption; last <- annotations.lastOption; if (first != last))
      yield last.timestamp - first.timestamp

  override def compare(that: Span) =
    java.lang.Long.compare(timestamp.getOrElse(0L), that.timestamp.getOrElse(0L))

  def serviceNames: Set[String] = annotations.flatMap(a => a.host.map(h => h.serviceName)).toSet

  /**
   * Tries to extract the best name of the service in this span. This depends on annotations
   * logged and prioritized names logged by the server over those logged by the client.
   */
  lazy val serviceName: Option[String] = {
    // Most authoritative is the label of the server's endpoint
    binaryAnnotations.find(_.key == Constants.ServerAddr).flatMap(_.host).map(_.serviceName) orElse
      // Next, the label of any server annotation, logged by an instrumented server
      serverSideAnnotations.flatMap(_.host).headOption.map(_.serviceName) orElse
      // Next is the label of the client's endpoint
      binaryAnnotations.find(_.key == Constants.ClientAddr).flatMap(_.host).map(_.serviceName) orElse
      // Finally, the label of any client annotation, logged by an instrumented client
      clientSideAnnotations.flatMap(_.host).headOption.map(_.serviceName)
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

    new Span(
      traceId,
      selectedName,
      id,
      parentId,
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
object Span {
  /**
   * Merge all the spans with the same id. This is used by span stores who
   * store partial spans and need them collated at query time.
   *
   * Spans without an annotation are filtered out, as they are not possible to
   * present on a timeline. This is because the timestamp and duration of a
   * span are derived from annotations.
   */
  def mergeById(spans: Seq[Span]): List[Span] = {
    val spanMap = new mutable.HashMap[Long, Span]
    spans.foreach(s => {
      val oldSpan = spanMap.get(s.id)
      oldSpan match {
        case Some(oldS) => {
          val merged = oldS.merge(s)
          spanMap.put(merged.id, merged)
        }
        case None => spanMap.put(s.id, s)
      }
    })
    spanMap.values.filter(_.annotations.nonEmpty).toList.sorted
  }
}
