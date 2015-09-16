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
package com.twitter.zipkin.receiver.scribe

import com.twitter.app.App
import com.twitter.finagle.{CancelledRequestException, ThriftMux}
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.logging.Logger
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Base64StringEncoder, Future, NonFatal, Return, Throw, Time}
import com.twitter.zipkin.collector.{QueueFullException, SpanReceiver}
import com.twitter.zipkin.thriftscala.{LogEntry, ResultCode, Scribe, Span => ThriftSpan}
import java.net.InetSocketAddress
import java.util.concurrent.CancellationException

/**
 * A SpanReceiverFactory that should be mixed into a base ZipkinCollector. This
 * provides `newScribeSpanReceiver` which will create a `ScribeSpanReceiver`
 * listening on a configurable port (-zipkin.receiver.scribe.port).
 */
trait ScribeSpanReceiverFactory { self: App =>
  val scribeAddr = flag(
    "zipkin.receiver.scribe.addr",
    new InetSocketAddress(1490),
    "the address to listen on")

  val scribeCategories = flag(
    "zipkin.receiver.scribe.categories",
    Seq("zipkin"),
    "a whitelist of categories to process")

  def newScribeSpanReceiver(
    process: SpanReceiver.Processor,
    stats: StatsReceiver = DefaultStatsReceiver.scope("ScribeSpanReceiver")
  ): SpanReceiver = new SpanReceiver {

    val service = ThriftMux.serveIface(
      scribeAddr(),
      new ScribeReceiver(scribeCategories().toSet, process, stats))

    def close(deadline: Time): Future[Unit] = closeAwaitably { service.close(deadline) }
  }
}

class ScribeReceiver(
  categories: Set[String],
  process: SpanReceiver.Processor,
  stats: StatsReceiver = DefaultStatsReceiver.scope("ScribeReceiver")
) extends Scribe[Future] {
  private[this] val deserializer = new BinaryThriftStructSerializer[ThriftSpan] {
    override val encoder = Base64StringEncoder
    val codec = ThriftSpan
  }

  private[this] val log = Logger.get

  private[this] val tryLater = Future.value(ResultCode.TryLater)
  private[this] val ok = Future.value(ResultCode.Ok)

  private[this] val logCallStat = stats.stat("logCallBatches")
  private[this] val pushbackCounter = stats.counter("pushBack")
  private[this] val errorStats = stats.scope("processingError")
  private[this] val fatalStats = stats.scope("fatalException")
  private[this] val batchesProcessedStat = stats.stat("processedBatches")
  private[this] val messagesStats = stats.scope("messages")
  private[this] val totalMessagesCounter = messagesStats.counter("total")
  private[this] val InvalidMessagesCounter = messagesStats.counter("invalid")
  private[this] val categoryCounters = categories.map { category =>
    val cat = category.toLowerCase
    (cat, messagesStats.scope("perCategory").counter(cat))
  }.toMap

  private[this] def entryToSpan(entry: LogEntry): Option[ThriftSpan] = try {
    Some(deserializer.fromString(entry.message))
  } catch {
    case e: Exception => {
      // scribe doesn't have any ResultCode.ERROR or similar
      // let's just swallow this invalid msg
      log.warning(e, "Invalid msg: %s", entry.message)
      InvalidMessagesCounter.incr()
      None
    }
  }

  def log(entries: Seq[LogEntry]): Future[ResultCode] = {
    logCallStat.add(entries.size)

    val spans = entries flatMap { entry =>
      totalMessagesCounter.incr()
      categoryCounters.get(entry.category.toLowerCase) flatMap { counter =>
        counter.incr()
        entryToSpan(entry)
      }
    }

    if (spans.isEmpty) ok else {
      process(spans) transform {
        case Return(_) =>
          batchesProcessedStat.add(spans.size)
          ok
        case Throw(NonFatal(e)) => (e, e.getCause) match {
          // It is not an error if the scribe client decided to cancel its request.
          // See Finagle FAQ's first entry http://twitter.github.io/finagle/guide/FAQ.html
          case (_: CancellationException, _: CancelledRequestException) => ok
          case (_: QueueFullException, _) => pushbackCounter.incr(); tryLater
          case _ =>
            log.warning("Sending TryLater due to %s(%s)"
              .format(e.getClass.getSimpleName, if (e.getMessage == null) "" else e.getMessage))
            errorStats.counter(e.getClass.getName).incr()
            tryLater
        }
        case Throw(e) =>
          fatalStats.counter(e.getClass.getName).incr()
          Future.exception(e)
      }
    }
  }
}
