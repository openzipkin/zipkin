package com.twitter.finagle.zipkin.thrift

import com.twitter.conversions.storage._
import com.twitter.finagle.httpx.{Method, Request}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.tracing.{NullTracer, Trace}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Httpx, param}
import com.twitter.io.Buf
import com.twitter.util._
import org.apache.thrift.protocol.{TBinaryProtocol, TList, TType}
import org.apache.thrift.transport.TMemoryBuffer

/**
 * Receives the Finagle generated traces and sends them off to Zipkin via http.
 *
 * @param hostname the value of the http host header to send.
 * @param statsReceiver see [[RawZipkinTracer.statsReceiver]]
 * @param timer see [[RawZipkinTracer.timer]]
 * @param poolSize see [[RawZipkinTracer.poolSize]]
 * @param initialBufferSize see [[RawZipkinTracer.initialBufferSize]]
 * @param maxBufferSize  see [[RawZipkinTracer.maxBufferSize]]
 */
class HttpZipkinTracer(
  hostname: String,
  statsReceiver: StatsReceiver,
  timer: Timer = DefaultTimer.twitter,
  poolSize: Int = 10,
  initialBufferSize: StorageUnit = 512.bytes,
  maxBufferSize: StorageUnit = 1.megabyte
) extends RawZipkinTracer(null, statsReceiver, timer, poolSize, initialBufferSize, maxBufferSize) {

  private[this] val client = Httpx.client.configured(param.Tracer(NullTracer)).newClient(hostname).toService
  private[this] val okCounter = statsReceiver.scope("log_span").counter("ok")
  private[this] val errorReceiver = statsReceiver.scope("log_span").scope("error")

  /** Logs spans via POST /api/v1/spans. */
  override def logSpans(spans: Seq[Span]): Future[Unit] = {
    // serialize all spans as a thrift list
    val serializedSpans = try {
      val transport = new TMemoryBuffer(0)
      val oproto = new TBinaryProtocol(transport)
      oproto.writeListBegin(new TList(TType.STRUCT, spans.size))
      spans.foreach(_.toThrift.write(oproto))
      oproto.writeListEnd()
      transport.getArray()
    } catch {
      case NonFatal(e) => errorReceiver.counter(e.getClass.getName).incr(); return Future.Unit
    }

    val request = Request(Method.Post, "/api/v1/spans")
    request.headerMap.add("Host", hostname)
    request.headerMap.add("Content-Type", "application/x-thrift")
    request.headerMap.add("Content-Length", serializedSpans.length.toString)
    request.content =  Buf.ByteArray.Owned(serializedSpans)

    Trace.letClear { // Don't recurse tracing by tracing sending spans to the collector.
      client(request)
        .onSuccess(_ => okCounter.incr())
        .rescue {
          case NonFatal(e) => errorReceiver.counter(e.getClass.getName).incr(); Future.Done
        }.unit
    }
  }
}
