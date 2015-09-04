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
import com.twitter.finagle.ThriftMux
import com.twitter.finagle.stats.{DefaultStatsReceiver, Stat, StatsReceiver}
import com.twitter.logging.Logger
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Base64StringEncoder, Future, NonFatal, Return, Throw, Time}
import com.twitter.zipkin.collector.{QueueFullException, SpanReceiver}
import com.twitter.zipkin.thriftscala.{LogEntry, ResultCode, Scribe, Span => ThriftSpan}
import java.net.InetSocketAddress

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
    val span = Stat.time(stats.stat("deserializeSpan")) { deserializer.fromString(entry.message) }
    Some(span)
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
        case Throw(NonFatal(e)) =>
          if (!e.isInstanceOf[QueueFullException])
            log.warning("Exception in process(): %s".format(e.getMessage))
          errorStats.counter(e.getClass.getName).incr()
          pushbackCounter.incr()
          tryLater
        case Throw(e) =>
          fatalStats.counter(e.getClass.getName).incr()
          Future.exception(e)
      }
    }
  }
}
