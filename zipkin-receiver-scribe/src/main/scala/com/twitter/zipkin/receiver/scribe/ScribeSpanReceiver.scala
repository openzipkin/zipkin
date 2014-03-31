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
import com.twitter.conversions.time._
import com.twitter.finagle.Thrift
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.util.{DefaultTimer, InetSocketAddressUtil}
import com.twitter.logging.Logger
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Closable, Future, NonFatal, Return, Throw, Time}
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen.{LogEntry, ResultCode, Scribe, Span => ThriftSpan, ZipkinCollector}
import com.twitter.zipkin.zookeeper._
import com.twitter.zk.ZkClient
import java.net.{InetSocketAddress, URI}

/**
 * A SpanReceiverFactory that should be mixed into a base ZipkinCollector. This
 * provides `newScribeSpanReceiver` which will create a `ScribeSpanReceiver`
 * listening on a configurable port (-zipkin.receiver.scribe.port) and announced to
 * ZooKeeper via a given path (-zipkin.receiver.scribe.zk.path). If a path is not
 * explicitly provided no announcement will be made (this is helpful for instance
 * during development). This factory must also be mixed into an App trait along with
 * a ZooKeeperClientFactory.
 */
trait ScribeSpanReceiverFactory { self: App with ZooKeeperClientFactory =>
  val scribeAddr = flag(
    "zipkin.receiver.scribe.addr",
    new InetSocketAddress(1490),
    "the address to listen on")

  val scribeCategories = flag(
    "zipkin.receiver.scribe.categories",
    Seq("zipkin"),
    "a whitelist of categories to process")

  val scribeZkPath = flag(
    "zipkin.receiver.scribe.zk.path",
    "/com/twitter/zipkin/receiver/scribe",
    "the zookeeper path to announce on. blank does not announce")

  def newScribeSpanReceiver(
    process: Seq[Span] => Future[Unit],
    stats: StatsReceiver = DefaultStatsReceiver.scope("ScribeSpanReceiver")
  ): SpanReceiver = new SpanReceiver {

    val zkNode: Option[Closable] = scribeZkPath.get.map { path =>
      val addr = InetSocketAddressUtil.toPublic(scribeAddr()).asInstanceOf[InetSocketAddress]
      val nodeName = "%s:%d".format(addr.getHostName, addr.getPort)
      zkClient.createEphemeral(path + "/" + nodeName, nodeName.getBytes)
    }

    val service = Thrift.serveIface(
      scribeAddr(),
      new ScribeReceiver(scribeCategories().toSet, process, stats))

    val closer: Closable = zkNode map { Closable.sequence(_, service) } getOrElse { service }
    def close(deadline: Time): Future[Unit] = closeAwaitably { closer.close(deadline) }
  }
}

class ScribeReceiver(
  categories: Set[String],
  process: Seq[Span] => Future[Unit],
  stats: StatsReceiver = DefaultStatsReceiver.scope("ScribeReceiver")
) extends Scribe[Future] {
  private[this] val deserializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }

  private[this] val log = Logger.get

  private[this] val tryLater = Future.value(ResultCode.TryLater)
  private[this] val ok = Future.value(ResultCode.Ok)

  private[this] val logCallStat = stats.stat("logCallBatches")
  private[this] val pushbackCounter = stats.counter("pushBack")
  private[this] val batchesProcessedStat = stats.stat("processedBatches")
  private[this] val messagesStats = stats.scope("messages")
  private[this] val totalMessagesCounter = messagesStats.counter("total")
  private[this] val InvalidMessagesCounter = messagesStats.counter("invalid")
  private[this] val categoryCounters = categories map { category =>
    val cat = category.toLowerCase
    (cat, messagesStats.scope("perCategory").counter(cat))
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
          pushbackCounter.incr()
          tryLater
        case Throw(e) =>
          Future.exception(e)
      }
    }
  }
}
