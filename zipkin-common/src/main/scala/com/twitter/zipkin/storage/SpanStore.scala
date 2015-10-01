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

import java.nio.ByteBuffer

import com.twitter.util.FuturePools._
import com.twitter.util.{Closable, Future}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span

abstract class SpanStore extends java.io.Closeable {

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   *
   * <p/> Results are sorted in order of the first span's timestamp, and contain
   * less elements than trace IDs when corresponding traces aren't available.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]]

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   *
   * <p/> Results are sorted in order of the first span's timestamp
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
   * Get all the service names for as far back as the ttl allows.
   *
   * <p/> Results are sorted lexicographically
   */
  def getAllServiceNames(): Future[Seq[String]]

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   *
   * <p/> Results are sorted lexicographically
   */
  def getSpanNames(service: String): Future[Seq[String]]

  /**
   * Store a list of spans, indexing as necessary.
   */
  def apply(spans: Seq[Span]): Future[Unit]

  /**
   * Close writes and await possible draining of internal queues.
   */
  override def close()
}

object SpanStore {

  /** Allows [[SpanStore]] to be used with a [[com.twitter.finagle.Filter]] */
  implicit def toScalaFunc(s: SpanStore): (Seq[Span] => Future[Unit]) = {
    return (spans: Seq[Span]) => s.apply(spans)
  }

  implicit def toTwitterCloseable(c: java.io.Closeable): Closable = {
    Closable.make(t => unboundedPool.apply(() => c.close()))
  }
}

class InMemorySpanStore extends SpanStore {
  import scala.collection.mutable

  val spans: mutable.ArrayBuffer[Span] = new mutable.ArrayBuffer[Span]

  private[this] def call[T](f: => T): Future[T] = synchronized { Future(f) }

  private[this] def spansForService(name: String): Seq[Span] =
    spans.filter { span =>
      span.serviceNames.exists { _.toLowerCase == name.toLowerCase }
    }.toList

  override def close() = {}

  override def apply(newSpans: Seq[Span]): Future[Unit] = call {
    spans ++= newSpans.map(s => s.copy(annotations = s.annotations.sorted))
  }.unit

  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = call {
    traceIds flatMap { id =>
      Some(spans.filter(_.traceId == id)).filter(_.length > 0)
    }
  }.map(_.sortBy(t => t.head.firstTimestamp))

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = call {
    ((spanName, spansForService(serviceName)) match {
      case (Some(name), spans) =>
        spans filter { _.name.toLowerCase == name.toLowerCase }
      case (_, spans) =>
        spans
    }).filter { span =>
      span.lastAnnotation match {
        case Some(ann) => ann.timestamp <= endTs
        case None => false
      }
    }.take(limit).map { span =>
      IndexedTraceId(span.traceId, span.lastAnnotation.get.timestamp)
    }.toList
  }

  override def getTraceIdsByAnnotation(
    serviceName: String,
    annotation: String,
    value: Option[ByteBuffer],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = call {
    // simulate the lack of index for core annotations
    if (Constants.CoreAnnotations.contains(annotation)) Seq.empty else {
      ((value, spansForService(serviceName)) match {
        case (Some(v), spans) =>
          spans filter { span =>
            span.lastAnnotation.isDefined &&
            span.lastAnnotation.get.timestamp <= endTs &&
            span.binaryAnnotations.exists { ba => ba.key == annotation && ba.value == v }
          }
        case (_, spans) =>
          spans filter { span =>
            span.lastAnnotation.isDefined &&
            span.annotations.min.timestamp <= endTs &&
            span.annotations.exists { _.value == annotation }
          }
      }).take(limit).map { span =>
        IndexedTraceId(span.traceId, span.lastAnnotation.get.timestamp)
      }.toList
    }
  }

  override def getAllServiceNames(): Future[Seq[String]] = call {
    spans.flatMap(_.serviceNames).distinct.toList.sorted
  }

  override def getSpanNames(serviceName: String): Future[Seq[String]] = call {
    spansForService(serviceName).map(_.name).distinct.toList.sorted
  }
}
