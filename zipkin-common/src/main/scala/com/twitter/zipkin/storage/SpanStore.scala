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

import com.twitter.conversions.time._
import com.twitter.finagle.{Filter => FFilter, Service}
import com.twitter.util.{Closable, CloseAwaitably, Duration, Future, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span
import java.nio.ByteBuffer
import scala.collection.mutable

trait SpanStore extends WriteSpanStore with ReadSpanStore

object SpanStore {
  type Filter = FFilter[Seq[Span], Unit, Seq[Span], Unit]
}


/**
 * A convenience builder to create a single WriteSpanStore from many. Writes
 * will be fanned out concurrently. A failure of any store will return a failure.
 * Any store logic should be handled per-store and then wrapped in this.
 */
object FanoutWriteSpanStore {
  def apply(stores: WriteSpanStore*): WriteSpanStore = new WriteSpanStore {
    def apply(spans: Seq[Span]): Future[Unit] =
      Future.join(stores map { _(spans) })

    def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] =
      Future.join(stores map { _.setTimeToLive(traceId, ttl) })

    override def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.all(stores: _*).close(deadline)
    }
  }
}

/**
 * Write store extends CloseAwaitably so we can close writes and await possible draining
 * of internal queues.
 */
trait WriteSpanStore
  extends (Seq[Span] => Future[Unit])
  with Closable
  with CloseAwaitably
{
  // store a list of spans
  def apply(spans: Seq[Span]): Future[Unit]

  // Used for pinning
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit]

  protected def shouldIndex(span: Span): Boolean =
    !(span.isClientSide() && span.serviceNames.contains("client"))
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
  def getAllServiceNames: Future[Set[String]]

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   */
  def getSpanNames(service: String): Future[Set[String]]
}

class InMemorySpanStore extends SpanStore {
  val ttls: mutable.Map[Long, Duration] = mutable.Map.empty
  val spans: mutable.ArrayBuffer[Span] = new mutable.ArrayBuffer[Span]

  private[this] def call[T](f: => T): Future[T] = synchronized { Future(f) }

  private[this] def spansForService(name: String): Seq[Span] =
    spans filter { span =>
      shouldIndex(span) &&
      span.serviceNames.exists { _.toLowerCase == name.toLowerCase }
    } toList

  def close(deadline: Time): Future[Unit] = closeAwaitably {
    Future.Done
  }

  def apply(newSpans: Seq[Span]): Future[Unit] = call {
    newSpans foreach { span => ttls(span.traceId) = 1.second }
    spans ++= newSpans
  }.unit

  // Used for pinning
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = call {
    ttls(traceId) = ttl
  }.unit

  def getTimeToLive(traceId: Long): Future[Duration] = call {
    ttls(traceId)
  }

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = call {
    spans.map(_.traceId).toSet & traceIds.toSet
  }

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = call {
    traceIds flatMap { id =>
      Some(spans filter { _.traceId == id } toList) filter { _.length > 0 }
    }
  }

  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = call {
    spans filter { _.traceId == traceId } toList
  }

  def getTraceIdsByName(
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
    }) filter { span =>
      span.lastAnnotation match {
        case Some(ann) => ann.timestamp <= endTs
        case None => false
      }
    } filter(shouldIndex) take(limit) map { span =>
      IndexedTraceId(span.traceId, span.lastAnnotation.get.timestamp)
    } toList
  }

  def getTraceIdsByAnnotation(
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
      }) filter(shouldIndex) take(limit) map { span =>
        IndexedTraceId(span.traceId, span.lastAnnotation.get.timestamp)
      } toList
    }
  }

  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = call {
    traceIds.map { traceId =>
      val timestamps = spans.filter { span => span.traceId == traceId }.flatMap { span =>
        Seq(span.firstAnnotation.map { _.timestamp }, span.lastAnnotation.map { _.timestamp }).flatten
      }

      if(timestamps.isEmpty)
        None
      else
        Some(TraceIdDuration(traceId, timestamps.max - timestamps.min, timestamps.min))
    }.flatten
  }

  def getAllServiceNames: Future[Set[String]] = call {
    spans.flatMap(_.serviceNames).toSet
  }

  def getSpanNames(serviceName: String): Future[Set[String]] = call {
    spansForService(serviceName).map(_.name).toSet
  }
}
