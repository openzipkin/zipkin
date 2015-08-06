package com.twitter.zipkin.collector

import com.twitter.conversions.time._
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Future}

/**
 * This queue inherits from ItemQueue but instead of throwing an exception when its full it sleeps
 * for a configurable amount of time until the span storer has enough time to consume records.
 */
class BlockingItemQueue [A, B](
  maxSize: Int,
  maxConcurrency: Int,
  process: A => Future[B],
  timeout: Duration = Duration.Top,
  sleepOnFull: Duration = 1.seconds,
  stats: StatsReceiver = DefaultStatsReceiver.scope("BlockingItemQueue"))
    extends ItemQueue[A, B](maxSize, maxConcurrency, process, timeout, stats) {

  val log = Logger(getClass())

  override def add(item: A): Future[Unit] = {
    if (!running) {
      QueueClosed
    }
    while (!queue.offer(item)) {
      queueFullCounter.incr()
      log.debug(f"Queue is full, retrying: ${queue.size()}%d/$maxSize%d")
      Thread.sleep(sleepOnFull.inMillis)
      log.debug(f"After sleeping: ${queue.size()}%d/$maxSize%d")
    }
    Future.Done
  }

}
