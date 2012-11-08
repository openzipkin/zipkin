/*
 * Copyright 2012 Tumblr Inc.
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
package com.twitter.zipkin.storage.redis

import java.nio.ByteBuffer
import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.Set
import com.twitter.finagle.redis.Client
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.Index
import com.twitter.zipkin.storage.TraceIdDuration
import com.twitter.finagle.redis.protocol.ZRangeResults
import com.twitter.zipkin.storage.IndexedTraceId
import com.twitter.zipkin.common.BinaryAnnotation
import com.twitter.zipkin.common.AnnotationType
import com.twitter.conversions.time.intToTimeableNumber
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import com.twitter.util.Time
import com.twitter.util.Duration

trait RedisIndex extends Index {

  val database: Client

  val ttl: Option[Duration]

  case class OptionSortedSetMap(_client: Client, firstPrefix: String, secondPrefix: String) {
    lazy val firstSetMap = new RedisSortedSetMap(_client, firstPrefix, ttl)
    lazy val secondSetMap = new RedisSortedSetMap(_client, secondPrefix, ttl)

    def get(primaryKey: String,
      secondaryKey: Option[String],
      start: Option[Double],
      stop: Double,
      count: Int) = secondaryKey match {
        case Some(contents) =>
          firstSetMap.get(redisJoin(primaryKey, contents), start.getOrElse(0), stop, count)
        case None => secondSetMap.get(primaryKey, start.getOrElse(0), stop, count)
      }

    def put(primaryKey: String, secondaryKey: Option[String], score: Double, value: ChannelBuffer) = {
      val first = secondaryKey map { secondValue =>
        firstSetMap.add(redisJoin(primaryKey, secondValue), score, value)
      }
      val second = secondSetMap.add(primaryKey, score, value)
      Future.join(Seq(first.getOrElse(Future.Unit), second))
    }
  }

  case class RedisSet(client: Client, key: String) {
    val _underlying = new RedisSetMap(client, "singleton", ttl)
    def get() = _underlying.get(key)
    def add(bytes: ChannelBuffer) = _underlying.add(key, bytes)
  }

  lazy val annotationsListMap = new RedisSortedSetMap(database, "annotations", ttl)
  lazy val binaryAnnotationsListMap = new RedisSortedSetMap(database, "binary_annotations", ttl)
  lazy val serviceSpanMap = OptionSortedSetMap(database, "service", "service:span")
  lazy val spanMap = new RedisSetMap(database, "span", ttl)
  lazy val serviceArray = new RedisSet(database, "services")
  lazy val traceHash = new RedisHash(database, "ttlMap")

  override def close() = {
    database.release()
  }

  private[this] def zRangeResultsToSeqIds(arr: ZRangeResults): Seq[IndexedTraceId] =
    arr.asTuples map (tup => IndexedTraceId(tup._1, tup._2.toLong))

  private[redis] def redisJoin(items: String*) = items.mkString(":")

  /**
   * gets trace ids by name
   */
  override def getTraceIdsByName(serviceName: String, span: Option[String],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] =
    serviceSpanMap.get(
      serviceName,
      span,
      ttl map (dur => endTs - dur.inMicroseconds),
      endTs,
      limit) map zRangeResultsToSeqIds

  /**
   * Get the trace ids for this annotation between the two timestamps. If value is also passed we expect
   * both the annotation key and value to be present in index for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  override def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = (value match {
      case Some(anno) =>
        binaryAnnotationsListMap.get(
          redisJoin(serviceName, annotation, ChannelBuffers.copiedBuffer(anno)),
          (ttl map (dur => (endTs - dur.inMicroseconds).toDouble)).getOrElse(0.0),
          endTs,
          limit)
      case None =>
        annotationsListMap.get(
          redisJoin(serviceName, annotation),
          (ttl map (dur => (endTs - dur.inMicroseconds).toDouble)).getOrElse(0.0),
          endTs,
          limit)
    }) map zRangeResultsToSeqIds

  /**
   * Fetch the duration or an estimate thereof from the traces.
   * Duration returned in micro seconds.
   */
  override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = Future.collect(
    traceIds map (getTraceDuration(_))
  ) map (_ flatten)

  private[this] def getTraceDuration(traceId: Long): Future[Option[TraceIdDuration]] =
    traceHash.get(traceId) map {
      _ flatMap { bytes =>
        val TimeRange(start, end) = decodeStartEnd(bytes)
        Some(TraceIdDuration(traceId, end - start, start))
      }
    }

  /**
   * Get all the service names for as far back as the ttl allows.
   */
  override def getServiceNames: Future[Set[String]] = serviceArray.get() map (serviceNames =>
    (serviceNames map (new String(_))).toSet
  )

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   */
  override def getSpanNames(service: String): Future[Set[String]] = spanMap.get(service) map (
    strings => (strings map (new String(_))).toSet
  )

  /**
   * Index a trace id on the service and name of a specific Span
   */
  override def indexTraceIdByServiceAndName(span: Span) : Future[Unit] = Future.join(
    (span.serviceNames toSeq) map { serviceName =>
      serviceSpanMap.put(serviceName, Some(span.name), span.lastAnnotation.get.timestamp, span.traceId)
    }
  )

  /**
   * Index the span by the annotations attached
   */
  override def indexSpanByAnnotations(span: Span) : Future[Unit] = Future.join(
    {
      def encodeAnnotation(bin: BinaryAnnotation): String = bin.annotationType match {
        case AnnotationType.Bool => (if (bin.value.get() != 0) true else false).toString
        case AnnotationType.Double => bin.value.getDouble.toString
        case AnnotationType.I16 => bin.value.getShort.toString
        case AnnotationType.I32 => bin.value.getInt.toString
        case AnnotationType.I64 => bin.value.getLong.toString
        case _ => ChannelBuffers.copiedBuffer(bin.value)
      }

      def binaryAnnoStringify(bin: BinaryAnnotation, service: String): String =
        redisJoin(service, bin.key, encodeAnnotation(bin))

      val time = span.lastAnnotation.get.timestamp
      val binaryAnnos: Seq[Future[Unit]] = span.serviceNames.toSeq flatMap { serviceName =>
        span.binaryAnnotations map { binaryAnno =>
          binaryAnnotationsListMap.add(
            binaryAnnoStringify(binaryAnno, serviceName),
            time,
            span.traceId
          )
          Future.Unit
        }
      }
      val annos = for (serviceName <- span.serviceNames toSeq;
        anno <- span.annotations)
        yield annotationsListMap.add(redisJoin(serviceName, anno.value), time, span.traceId)
      annos ++ binaryAnnos
    }
  )

  /**
   * Store the service name, so that we easily can
   * find out which services have been called from now and back to the ttl
   */
  override def indexServiceName(span: Span): Future[Unit] = Future.join(
    for (serviceName <- span.serviceNames.toSeq
      if serviceName != "")
      yield serviceArray.add(serviceName))

  /**
   * Index the span name on the service name. This is so we
   * can get a list of span names when given a service name.
   * Mainly for UI purposes
   */
  override def indexSpanNameByService(span: Span): Future[Unit] = if (span.name != "") Future.join(
    for (serviceName <- span.serviceNames.toSeq
      if serviceName != "")
      yield spanMap.add(serviceName, span.name)
  ) else Future.Unit

  /**
   * Index a span's duration. This is so we can look up the trace duration.
   */
  override def indexSpanDuration(span: Span): Future[Void] = {
    traceHash.get(span.traceId) map {
      case None => TimeRange.fromSpan(span) map { timeRange =>
        traceHash.put(span.traceId, timeRange)
      }
      case Some(bytes) => indexNewStartAndEnd(span, bytes)
    }
    Future.Void
  }

  private[this] def indexNewStartAndEnd(span: Span, buf: ChannelBuffer) =
    TimeRange.fromSpan(span) map { timeRange =>
      traceHash.put(span.traceId, timeRange.widen(buf))
    }

}
