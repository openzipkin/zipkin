package com.twitter.finagle.zipkin.thrift

import com.twitter.conversions.storage._
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.SpanStore
import org.apache.thrift.protocol.{TBinaryProtocol, TList, TType}
import org.apache.thrift.transport.TMemoryBuffer

/**
 * Receives the Finagle generated traces and sends directly to a [[SpanStore]].
 *
 * @param spanStore the value of the http host header to send.
 * @param statsReceiver see [[RawZipkinTracer.statsReceiver]]
 * @param timer see [[RawZipkinTracer.timer]]
 * @param poolSize see [[RawZipkinTracer.poolSize]]
 * @param initialBufferSize see [[RawZipkinTracer.initialBufferSize]]
 * @param maxBufferSize  see [[RawZipkinTracer.maxBufferSize]]
 */
class SpanStoreZipkinTracer(
  spanStore: SpanStore,
  statsReceiver: StatsReceiver,
  timer: Timer = DefaultTimer.twitter,
  poolSize: Int = 10,
  initialBufferSize: StorageUnit = 512.bytes,
  maxBufferSize: StorageUnit = 1.megabyte
) extends RawZipkinTracer(null, statsReceiver, timer, poolSize, initialBufferSize, maxBufferSize) {

  private[this] val okCounter = statsReceiver.scope("log_span").counter("ok")
  private[this] val errorReceiver = statsReceiver.scope("log_span").scope("error")

  /** Logs spans directly to a SpanStore. */
  override def logSpans(spans: Seq[Span]): Future[Unit] = {
    // Finagle uses different thrift structures than zipkin's.
    // Serialize them into a thrift list for easier conversion.
    val zipkinSpans = try {
      val transport = new TMemoryBuffer(0)
      val oproto = new TBinaryProtocol(transport)
      oproto.writeListBegin(new TList(TType.STRUCT, spans.size))
      spans.map(_.toThrift).foreach(_.write(oproto))
      oproto.writeListEnd()
      thriftListToSpans(transport.getArray)
    } catch {
      case NonFatal(e) => errorReceiver.counter(e.getClass.getName).incr(); return Future.Unit
    }

    spanStore(zipkinSpans)
      .onSuccess(_ => okCounter.incr())
      .rescue {
        case NonFatal(e) => errorReceiver.counter(e.getClass.getName).incr(); Future.Done
      }.unit
  }
}
