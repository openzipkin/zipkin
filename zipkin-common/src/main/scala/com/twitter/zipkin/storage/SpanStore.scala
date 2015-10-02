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
import com.twitter.zipkin.common.{Trace, Span}

abstract class SpanStore extends java.io.Closeable {

  /**
   * Get the available trace information from the storage system.
   * Spans in trace are sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   *
   * <p/> Results are sorted in order of the first span's timestamp, and contain
   * up to [[QueryRequest.limit]] elements.
   */
  def getTraces(qr: QueryRequest): Future[Seq[Seq[Span]]]

  /**
   * Get the available trace information from the storage system.
   * Spans in trace are sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   *
   * <p/> Results are sorted in order of the first span's timestamp, and contain
   * less elements than trace IDs when corresponding traces aren't available.
   */
  def getTracesByIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]]

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
   *
   * <p/> Spans may come in sparse, for example apply may be called multiple times
   * with a span with the same id, containing different annotations. The
   * implementation should ensure these are merged at query time.
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

class InMemorySpanStore extends SpanStore with CollectAnnotationQueries {

  import scala.collection.mutable

  val spans: mutable.ArrayBuffer[Span] = new mutable.ArrayBuffer[Span]

  private[this] def call[T](f: => T): Future[T] = synchronized(Future(f))

  private[this] def spansForService(name: String): Seq[Span] =
    spans.filter { span =>
      span.serviceNames.exists(_.toLowerCase == name.toLowerCase)
    }.toList

  override def close() = {}

  override def apply(newSpans: Seq[Span]): Future[Unit] = call {
    spans ++= newSpans.map(s => s.copy(annotations = s.annotations.sorted))
  }.unit

  override def getTracesByIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = call {
    spans.groupBy(_.traceId)
         .filterKeys(traceIds.contains(_))
         .values.filter(!_.isEmpty)
         .map(Trace(_).spans).toList
         .sortBy(_.head.firstTimestamp)
  }

  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int
  ): Future[Seq[IndexedTraceId]] = call {
    ((spanName, spansForService(serviceName)) match {
      case (Some(name), spans) => spans filter(_.name.toLowerCase == name.toLowerCase)
      case (_, spans) => spans
    }).filter { span =>
      span.lastTimestamp.map(_ <= endTs).getOrElse(false)
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
    if (Constants.CoreAnnotations.contains(annotation)) Seq.empty
    else {
      spansForService(serviceName)
        .filter(_.lastTimestamp.map(_ <= endTs).getOrElse(false))
        .filter(if (value.isDefined) {
        _.binaryAnnotations.exists(ba => ba.key == annotation && ba.value == value.get)
      } else {
        _.annotations.exists(_.value == annotation)
      })
        .take(limit)
        .map(span => IndexedTraceId(span.traceId, span.lastTimestamp.get))
        .toList
    }
  }

  override def getAllServiceNames(): Future[Seq[String]] = call {
    spans.flatMap(_.serviceNames).distinct.toList.sorted
  }

  override def getSpanNames(serviceName: String): Future[Seq[String]] = call {
    spansForService(serviceName).map(_.name).distinct.toList.sorted
  }
}
