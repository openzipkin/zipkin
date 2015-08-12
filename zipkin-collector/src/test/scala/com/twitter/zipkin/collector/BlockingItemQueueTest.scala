package com.twitter.zipkin.collector

import com.twitter.conversions.time._
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.util.{Await, Future, FuturePool}
import java.util.concurrent.CountDownLatch
import scala.util.Try
import org.scalatest._

class BlockingItemQueueTest extends FunSuite {

  val Item = ()
  val latch = new CountDownLatch(1)

  def fill(queue: BlockingItemQueue[Unit, Unit], items: Int): Future[Boolean] = {
    val results = (0 until items) map { _ =>
      queue.add(Item) transform { e => Future.value(e) }
    }
    Future.collect(results).map(_.forall(_.isReturn))
  }

  test("Sleeps on a max queue and waits for the worker to drain") {
    val expectedItemCount = 12
    val queueSize = 10
    val stats:InMemoryStatsReceiver = new InMemoryStatsReceiver()
    val queue = new BlockingItemQueue[Unit, Unit](queueSize, 1, fallingBehindWorker, 100.millis, 100.millis, stats)


    // Add 11 and not 10 because the first one that's going to be added will be consumed right away
    assert(Await.result(fill(queue, queueSize + 1)))

    // add an item, expect the queueFull stat to get incremented we need this to return because
    // the blocking queue will block because its full, so we use a future
    FuturePool.unboundedPool { queue.add(Item) }
    // poll for when the queueFull counter gets incremented or timeout
    poll( () => stats.counter("queueFull").apply() >= 1 )
    // Items are sitting in the queue blocking and not processing, this unblocks them
    latch.countDown()

    // Wait for all items to be processed
    poll( () => queue.size() == 0 )
    Await.ready(queue.close())

    // All items should be processed
    poll( () => stats.counter("successes").apply() == expectedItemCount )
    assert(queue.size() == 0)
    assert(stats.counter("queueFull").apply() >= 1)
  }

  def poll(f: () => Boolean): Boolean = {
    for (a <- 1 to 5) {
      if (f()) return true else Thread.sleep(1000)
    }
    false
  }

  def fallingBehindWorker(param: Unit): Future[Unit] = {
    //block instead of sleeping
    Future { latch.await(); param }
  }

}
