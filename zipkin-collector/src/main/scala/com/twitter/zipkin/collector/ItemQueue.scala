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

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.util._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ArrayBlockingQueue, Executors, TimeUnit}

class QueueFullException(size: Int) extends Exception("Queue is full. MaxSize: %d".format(size))
class QueueClosedException extends Exception("Queue is closed")

/**
 * A queue with configurable size and concurrency characteristics.
 *
 * This queue is backed by ArrayBlockingQueue of `maxSize`. Items are processed by a number of workers
 * limited by `maxConcurrency`. Each worker will pull an item from the queue and process it via the
 * provided `process` function. Workers will Await on `process` thus backpressure is based on the workers'
 * ability to fully process an item.
 *
 * On close the queue will drain itelf before completing the close Future.
 *
 * The queue can be awaited on and will not complete until it's been closed and drained.
 */
class ItemQueue[A, B](
  maxSize: Int,
  maxConcurrency: Int,
  process: A => Future[B],
  timeout: Duration = Duration.Top,
  stats: StatsReceiver = DefaultStatsReceiver.scope("ItemQueue")
) extends Closable with CloseAwaitably {
  @volatile private[this] var running: Boolean = true

  private[this] val queue = new ArrayBlockingQueue[A](maxSize)
  private[this] val queueSizeGauge = stats.addGauge("queueSize") { queue.size }
  private[this] val queueFullCounter = stats.counter("queueFull")
  private[this] val activeWorkers = new AtomicInteger(0)
  private[this] val activeWorkerGauge = stats.addGauge("activeWorkers") { activeWorkers.get }
  private[this] val maxConcurrencyGauge = stats.addGauge("maxConcurrency") { maxConcurrency }
  private[this] val failuresCounter = stats.counter("failures")
  private[this] val successesCounter = stats.counter("successes")

  private[this] val futurePool = new ExecutorServiceFuturePool(Executors.newCachedThreadPool(
    new NamedPoolThreadFactory("ItemQueuePool", makeDaemons = true)))
  private[this] val workers = Seq.fill(maxConcurrency) { futurePool(loop()) }

  private[this] def loop() {
    while (running || !queue.isEmpty) {
      val item = queue.poll(500, TimeUnit.MILLISECONDS)
      if (item != null) {
        activeWorkers.incrementAndGet()
        Try(Await.result(stats.timeFuture("processing_time_ms")(process(item)), timeout))
          .onSuccess(_ => successesCounter.incr())
          .onFailure(_ => failuresCounter.incr())
        activeWorkers.decrementAndGet()
      }
    }
  }

  def close(deadline: Time): Future[Unit] = closeAwaitably {
    running = false
    Future.join(workers)
  }

  private[this] val QueueFull = Future.exception(new QueueFullException(maxSize))
  private[this] val QueueClosed = Future.exception(new QueueClosedException)

  def add(item: A): Future[Unit] =
    if (!running) {
      QueueClosed
    } else if (!queue.offer(item)) {
      queueFullCounter.incr()
      QueueFull
    } else {
      Future.Done
    }
}
