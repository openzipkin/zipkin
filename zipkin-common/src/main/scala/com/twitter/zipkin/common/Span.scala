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
import scala.collection.breakOut
import scala.util.hashing.MurmurHash3

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
 * @param _name name of span, can be rpc method name for example, in lowercase.
 * @param id random long that identifies this span
 * @param parentId reference to the parent span in the trace tree
 * @param annotations annotations, containing a timestamp and some value. both user generated and
 * some fixed ones from the tracing framework. Sorted ascending by timestamp
 * @param binaryAnnotations  binary annotations, can contain more detailed information such as
 * serialized objects. Sorted ascending by timestamp. Sorted ascending by timestamp
 * @param debug if this is set we will make sure this span is stored, no matter what the samplers want
 */
// This is not a case-class as we need to enforce name as lowercase
class Span(
  val traceId: Long,
  _name: String,
  val id: Long,
  val parentId: Option[Long],
  val annotations: List[Annotation],
  val binaryAnnotations: Seq[BinaryAnnotation],
  val debug: Option[Boolean]) extends Ordered[Span] {

  /** name of span, can be rpc method name for example, in lowercase. */
  val name: String = _name.toLowerCase

  override def compare(that: Span) =
    java.lang.Long.compare(startTs.getOrElse(0L), that.startTs.getOrElse(0L))

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
   * Iterate through list of annotations and return the one with the given value.
   */
  def getAnnotation(value: String): Option[Annotation] =
    annotations.find(_.value == value)

  /**
   * Iterate through list of binaryAnnotations and return the one with the given key.
   */
  def getBinaryAnnotation(key: String): Option[BinaryAnnotation] =
    binaryAnnotations.find(_.key == key)

  /**
   * Take two spans with the same span id and merge all data into one of them.
   */
  def mergeSpan(mergeFrom: Span): Span = {
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
   * Get the first annotation by timestamp.
   */
  def firstAnnotation: Option[Annotation] = annotations.headOption

  /**
   * Get the last annotation by timestamp.
   */
  def lastAnnotation: Option[Annotation] = annotations.lastOption

  /**
   * Endpoints involved in this span
   */
  def endpoints: Set[Endpoint] =
    annotations.flatMap(_.host).toSet

  /**
   * Endpoint that is likely the owner of this span
   */
  def clientSideEndpoint: Option[Endpoint] =
    clientSideAnnotations.map(_.host).flatten.headOption

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

  /**
   * Duration of this span. May be None if we cannot find any annotations.
   */
  def duration: Option[Long] =
    for (first <- firstAnnotation; last <- lastAnnotation)
      yield last.timestamp - first.timestamp

  /**
   * @return true  if Span contains at most one of each core annotation
   *         false otherwise
   */
  def isValid: Boolean = {
    Constants.CoreAnnotations.map { c =>
      annotations.filter(_.value == c).length > 1
    }.count(b => b) == 0
  }

  /**
   * Get the annotations as a map with value to annotation bindings.
   */
  def getAnnotationsAsMap(): Map[String, Annotation] =
    annotations.map(a => a.value -> a)(breakOut)

  lazy val endTs: Option[Long] = lastAnnotation.map(_.timestamp)
  lazy val startTs: Option[Long] = firstAnnotation.map(_.timestamp)

  override lazy val hashCode =
    MurmurHash3.seqHash(List(traceId, name, id, parentId, annotations, binaryAnnotations, debug))

  override def equals(other: Any) = other match {
    case x: Span =>
      x.traceId == traceId && x.name == name && x.id == id && x.parentId == parentId &&
        x.annotations == annotations && x.binaryAnnotations == binaryAnnotations &&
        x.debug == debug
    case _ =>
      false
  }

  def copy(
    traceId: Long = this.traceId,
    name: String = this.name,
    id: Long = this.id,
    parentId: Option[Long] = this.parentId,
    annotations: List[Annotation] = this.annotations,
    binaryAnnotations: Seq[BinaryAnnotation] = this.binaryAnnotations,
    debug: Option[Boolean] = this.debug
  ) = Span(traceId, name, id, parentId, annotations, binaryAnnotations, debug)
}

object Span {
  def apply(
    traceId: Long,
    name: String,
    id: Long,
    parentId: Option[Long] = None,
    annotations: List[Annotation] = List.empty,
    binaryAnnotations: Seq[BinaryAnnotation] = Seq.empty,
    debug: Option[Boolean] = None
  ) = new Span(traceId, name, id, parentId, annotations, binaryAnnotations, debug)
}
