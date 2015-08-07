package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets.UTF_8
import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation, Span}
import com.twitter.zipkin.storage.{Index, IndexedTraceId, TraceIdDuration}
import java.nio.ByteBuffer
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer

/**
 * @param client the redis client to use
 * @param ttl expires keys older than this many seconds.
 */
class RedisIndex(
  val client: Client,
  val ttl: Option[Duration]
) extends Index {

  private case class SpanKey(service: String, span: String)

  private case class AnnotationKey(service: String, annotation: String)

  private case class BinaryAnnotationKey(service: String, annotation: String, value: String)

  private[this] val serviceIndex = new TraceIndex[String](client, ttl) {
    override def encodeKey(key: String) =
      copiedBuffer("service:" + key, UTF_8)
  }
  private[this] val spanIndex = new TraceIndex[SpanKey](client, ttl) {
    override def encodeKey(key: SpanKey) =
      copiedBuffer("service:span:%s:%s".format(key.service, key.span), UTF_8)
  }
  private[this] val annotationIndex = new TraceIndex[AnnotationKey](client, ttl) {
    override def encodeKey(key: AnnotationKey) =
      copiedBuffer("annotations:%s:%s".format(key.service, key.annotation), UTF_8)
  }
  private[this] val binaryAnnotationIndex = new TraceIndex[BinaryAnnotationKey](client, ttl) {
    override def encodeKey(key: BinaryAnnotationKey) =
      copiedBuffer("binary_annotations:%s:%s:%s".format(key.service, key.annotation, key.value), UTF_8)
  }
  private[this] val spanNames = new SetMultimap(client, ttl, "span")
  private[this] val serviceNames = new SetMultimap(client, ttl, "singleton")
  // TODO: bad and misleading name. should ttlMap be something else?
  private[this] val timeRanges = new TimeRangeStore(client, "ttlMap")

  override def close() = {
    client.release()
  }

  override def getTraceIdsByName(
    serviceName: String, spanName: Option[String],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    if (spanName.isDefined) {
      spanIndex.list(SpanKey(serviceName, spanName.get), endTs, limit)
    } else {
      serviceIndex.list(serviceName, endTs, limit)
    }
  }

  override def getTraceIdsByAnnotation(
    serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    if (value.isDefined) {
      val string = copiedBuffer(value.get).toString(UTF_8)
      binaryAnnotationIndex.list(BinaryAnnotationKey(serviceName, annotation, string), endTs, limit)
    } else {
      annotationIndex.list(AnnotationKey(serviceName, annotation), endTs, limit)
    }
  }

  override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] =
    Future.collect(traceIds map getTraceDuration) map (_.flatten)

  private[this] def getTraceDuration(traceId: Long): Future[Option[TraceIdDuration]] =
    timeRanges.get(traceId)
      .map(_.flatMap(r => Some(TraceIdDuration(traceId, r.stopTs - r.startTs, r.startTs))))

  override def getServiceNames = serviceNames.get("services")

  override def getSpanNames(service: String) = spanNames.get(service)

  override def indexTraceIdByServiceAndName(span: Span): Future[Unit] = {
    if (span.lastAnnotation.isEmpty) {
      return Future.Unit
    }
    val lastTimestamp = span.lastAnnotation.get.timestamp
    Future.join(
      span.serviceNames.toSeq.flatMap(serviceName =>
        Seq(
          spanIndex.add(SpanKey(serviceName, span.name), lastTimestamp, span.traceId),
          serviceIndex.add(serviceName, lastTimestamp, span.traceId)
        )
      )
    )
  }

  override def indexSpanByAnnotations(span: Span): Future[Unit] = {
    if (span.lastAnnotation.isEmpty) {
      return Future.Unit
    }
    val lastTimestamp = span.lastAnnotation.get.timestamp
    Future.join({
      val binaryAnnos = span.serviceNames.toSeq.flatMap(serviceName =>
        span.binaryAnnotations
          .map(bin => BinaryAnnotationKey(serviceName, bin.key, encode(bin)))
          .map(binaryAnnotationIndex.add(_, lastTimestamp, span.traceId)
          ))
      val annos = span.serviceNames.toSeq.flatMap(serviceName =>
        span.annotations.map(_.value)
          .filter(!Constants.CoreAnnotations.contains(_))
          .map(AnnotationKey(serviceName, _))
          .map(annotationIndex.add(_, lastTimestamp, span.traceId)
          ))
      annos ++ binaryAnnos
    })
  }

  override def indexServiceName(span: Span): Future[Unit] = Future.collect(
    span.serviceNames.toSeq
      .filter(_ != "")
      .map(serviceNames.put("services", _))
  ).unit

  override def indexSpanNameByService(span: Span): Future[Unit] = {
    if (span.name == "") {
      return Future.Unit
    }
    Future.join(
      span.serviceNames.toSeq
        .filter(_ != "")
        .map(spanNames.put(_, span.name))
    ).unit
  }

  override def indexSpanDuration(span: Span): Future[Unit] = {
    val inputRange = for (first <- span.firstAnnotation;
                          last <- span.lastAnnotation)
      yield TimeRange(first.timestamp, last.timestamp)
    inputRange.map(range =>
      timeRanges.get(span.traceId).map {
        case None => timeRanges.put(span.traceId, range)
        case Some(existing) => timeRanges.put(span.traceId, union(range, existing))
      }.unit
    ).getOrElse(Future.Unit)
  }

  private def union(left: TimeRange, right: TimeRange) =
    TimeRange(Math.min(left.startTs, right.startTs), Math.max(left.stopTs, right.stopTs))

  private def encode(bin: BinaryAnnotation): String = bin.annotationType match {
    case AnnotationType.Bool => (if (bin.value.get() != 0) true else false).toString
    case AnnotationType.Double => bin.value.getDouble.toString
    case AnnotationType.I16 => bin.value.getShort.toString
    case AnnotationType.I32 => bin.value.getInt.toString
    case AnnotationType.I64 => bin.value.getLong.toString
    case _ => copiedBuffer(bin.value).toString(UTF_8)
  }
}
