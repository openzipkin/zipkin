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
package com.twitter.zipkin.collector

import com.twitter.app.App
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.{Await, Closable, CloseAwaitably, Duration, Future, Time}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.WriteSpanStore

sealed trait AwaitableCloser extends Closable with CloseAwaitably

/**
 * A basic collector from which to create a server. Your collector will extend this
 * and implement `newReceiver` and `newSpanStore`. The base collector glues those two
 * together.
 */
trait ZipkinCollectorFactory {
  def newReceiver(receive: Seq[Span] => Future[Unit], stats: StatsReceiver): SpanReceiver
  def newSpanStore(stats: StatsReceiver): WriteSpanStore

  def newCollector(stats: StatsReceiver): AwaitableCloser = new AwaitableCloser {
    val store = newSpanStore(stats)
    val receiver = newReceiver(store(_), stats)

    def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.sequence(receiver, store).close(deadline)
    }
  }
}

/**
 * A base collector that inserts a configurable queue between the receiver and store.
 */
trait ZipkinQueuedCollectorFactory extends ZipkinCollectorFactory { self: App =>
  val itemQueueMax = flag("zipkin.itemQueue.maxSize", 500, "max number of span items to buffer")
  val itemQueueConcurrency = flag("zipkin.itemQueue.concurrency", 10, "number of concurrent workers to process the write queue")

  override def newCollector(stats: StatsReceiver): AwaitableCloser = new AwaitableCloser {
    val store = newSpanStore(stats)

    val queue = new ItemQueue[Seq[Span]](
      itemQueueMax(), itemQueueConcurrency(), store(_), stats.scope("ItemQueue"))

    val receiver = newReceiver(queue.add(_), stats)

    def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.sequence(receiver, queue, store).close(deadline)
    }
  }
}
