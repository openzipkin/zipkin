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

import com.twitter.app.{App, Flaggable}
import com.twitter.finagle.Thrift
import com.twitter.finagle.stats.{LoadedStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.{DefaultTimer, InetSocketAddressUtil}
import com.twitter.logging.Logger
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Closable, Future, Return, Throw, Time}
import com.twitter.conversions.time._
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen.{LogEntry, ResultCode, Scribe, Span => ThriftSpan, ZipkinCollector}
import com.twitter.zk.ZkClient
import java.net.{InetSocketAddress, URI}

/**
 * A SpanReceiverFactory that should be mixed into a base ZipkinCollector. This provides `newScribeSpanReceiver` which
 * will create a `ScribeSpanReciever` listening on a configurable port (-zipkin.receiver.scribe.port) and announced to
 * ZooKeeper via a given URI (-zipkin.receiver.scribe.announceURI). If a URI is not explicitly provided no announcement
 * will be made (this is helpful for instance during development).
 */
trait ScribeSpanReceiverFactory { self: App =>
  implicit object flagOfURI extends Flaggable[URI] {
    def parse(v: String) = new URI(v)
    override def show(c: URI) = c.toString
  }

  val scribePort = flag("zipkin.receiver.scribe.port", 1490, "the port to listen on")
  val scribeCategories = flag("zipkin.receiver.scribe.categories", Seq("zipkin"), "a whitelist of categories to process")
  val scribeAnnounceURI = flag("zipkin.receiver.scribe.annoucnceURI", new URI("none"), "the zookeeper URI to announce on. blank does not announce")

  def newScribeSpanReceiver(
    process: Seq[Span] => Future[Unit],
    stats: StatsReceiver = LoadedStatsReceiver.scope("ScribeSpanReceiver")
  ): SpanReceiver = new SpanReceiver {
    val boundAddress = InetSocketAddressUtil.toPublic(new InetSocketAddress(scribePort())).asInstanceOf[InetSocketAddress]

    val announcer: Option[Closable] = scribeAnnounceURI.get.map { uri =>
      implicit val timer = DefaultTimer.twitter
      val client = ZkClient("%s:%d".format(uri.getHost, uri.getPort), 3.seconds)
      new ScribeZKAnnouncer(uri.getPath, boundAddress, client, stats.scope("ZKAnnouncer"))
    }

    val service = Thrift.serveIface(boundAddress, new ScribeReceiver(scribeCategories().toSet, process, stats))

    val closer: Closable = announcer map { Closable.sequence(_, service) } getOrElse { service }
    def close(deadline: Time): Future[Unit] = closeAwaitably { closer.close(deadline) }
  }
}

class ScribeReceiver(
  categories: Set[String],
  process: Seq[Span] => Future[Unit],
  stats: StatsReceiver = LoadedStatsReceiver.scope("ScribeReceiver")
) extends Scribe[Future] {
  private[this] val deserializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }

  private[this] val log = Logger.get

  private[this] val TryLater = Future.value(ResultCode.TryLater)
  private[this] val Ok = Future.value(ResultCode.Ok)

  private[this] val LogCallStat = stats.stat("logCallBatches")
  private[this] val PushbackCounter = stats.counter("pushBack")
  private[this] val BatchesProcessedStat = stats.stat("processedBatches")
  private[this] val MessagesStats = stats.scope("messages")
  private[this] val TotalMessagesCounter = MessagesStats.counter("total")
  private[this] val InvalidMessagesCounter = MessagesStats.counter("invalid")
  private[this] val CategoryCounters = categories map { category =>
    val cat = category.toLowerCase
    (cat, MessagesStats.scope("perCategory").counter(cat))
  } toMap

  private[this] def entryToSpan(entry: LogEntry): Option[Span] = try {
    val span = stats.time("deserializeSpan") { deserializer.fromString(entry.message).toSpan }
    log.ifDebug("Processing span: " + span + " from " + entry.message)
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
    LogCallStat.add(entries.size)

    val spans = entries flatMap { entry =>
      TotalMessagesCounter.incr()
      CategoryCounters.get(entry.category.toLowerCase) flatMap { counter =>
        counter.incr()
        entryToSpan(entry)
      }
    }

    if (spans.isEmpty) Ok else {
      process(spans) transform {
        case Return(_) =>
          BatchesProcessedStat.add(spans.size)
          Ok
        case Throw(e) =>
          PushbackCounter.incr()
          TryLater
      }
    }
  }
}
