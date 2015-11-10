package com.twitter.zipkin.storage.redis

import java.io.Closeable
import java.nio.ByteBuffer

import com.google.common.base.Charsets.UTF_8
import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation, Span}
import com.twitter.zipkin.storage.IndexedTraceId
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer

import scala.collection.mutable

/**
 * @param client the redis client to use
 * @param ttl expires keys older than this many seconds.
 */
class RedisIndex(
  val client: Client,
  val ttl: Option[Duration]
) extends Closeable {

  private case class SpanKey(service: String, span: String)

  private case class AnnotationKey(service: String, annotation: String)

  private case class BinaryAnnotationKey(service: String, annotation: String, value: String)

  private[this] val serviceIndex = new TraceIndex[String](client, ttl) {
    def encodeKey(key: String) =
      copiedBuffer("service:" + key, UTF_8)
  }
  private[this] val spanIndex = new TraceIndex[SpanKey](client, ttl) {
    def encodeKey(key: SpanKey) =
      copiedBuffer("service:span:%s:%s".format(key.service, key.span), UTF_8)
  }
  private[this] val annotationIndex = new TraceIndex[AnnotationKey](client, ttl) {
    def encodeKey(key: AnnotationKey) =
      copiedBuffer("annotations:%s:%s".format(key.service, key.annotation), UTF_8)
  }
  private[this] val binaryAnnotationIndex = new TraceIndex[BinaryAnnotationKey](client, ttl) {
    def encodeKey(key: BinaryAnnotationKey) =
      copiedBuffer("binary_annotations:%s:%s:%s".format(key.service, key.annotation, key.value), UTF_8)
  }
  private[this] val spanNames = new SetMultimap(client, ttl, "span")
  private[this] val serviceNames = new SetMultimap(client, ttl, "singleton")

  override def close() = client.release()

  def getTraceIdsByName(
    serviceName: String, spanName: Option[String],
    endTs: Long, lookback: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    if (spanName.isDefined) {
      spanIndex.list(SpanKey(serviceName, spanName.get), endTs, lookback, limit)
    } else {
      serviceIndex.list(serviceName, endTs, lookback, limit)
    }
  }

  def getTraceIdsByAnnotation(
    serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, lookback: Long,limit: Int): Future[Seq[IndexedTraceId]] = {
    if (value.isDefined) {
      val string = copiedBuffer(value.get).toString(UTF_8)
      binaryAnnotationIndex.list(BinaryAnnotationKey(serviceName, annotation, string), endTs, lookback, limit)
    } else {
      annotationIndex.list(AnnotationKey(serviceName, annotation), endTs, lookback, limit)
    }
  }

  def getServiceNames = serviceNames.get("services")

  def getSpanNames(service: String) = spanNames.get(service)

  def index(span: Span): Future[Unit] = {
    val result = new mutable.MutableList[Future[Unit]]

    val services = span.serviceNames.filter(_ != "")

    result ++= services.map(serviceNames.put("services", _))

    if (span.name != "") {
      result ++= services.map(spanNames.put(_, span.name))
    }

    if (span.timestamp.isDefined) {
      val timestamp = span.timestamp.get

      result ++= services.map(serviceName =>
        serviceIndex.add(serviceName, timestamp, span.traceId))

      result ++= services.map(serviceName =>
        spanIndex.add(SpanKey(serviceName, span.name), timestamp, span.traceId))

      result ++= services.flatMap(serviceName =>
        span.annotations.map(_.value)
          .map(AnnotationKey(serviceName, _))
          .map(annotationIndex.add(_, timestamp, span.traceId)))

      result ++= services.flatMap(serviceName =>
        span.binaryAnnotations
          .map(bin => BinaryAnnotationKey(serviceName, bin.key, encode(bin)))
          .map(binaryAnnotationIndex.add(_, timestamp, span.traceId)))
    }

    Future.join(result)
  }

  private def encode(bin: BinaryAnnotation): String = bin.annotationType match {
    case AnnotationType.Bool => (if (bin.value.get() != 0) true else false).toString
    case AnnotationType.Double => bin.value.getDouble.toString
    case AnnotationType.I16 => bin.value.getShort.toString
    case AnnotationType.I32 => bin.value.getInt.toString
    case AnnotationType.I64 => bin.value.getLong.toString
    case _ => copiedBuffer(bin.value).toString(UTF_8)
  }
}
