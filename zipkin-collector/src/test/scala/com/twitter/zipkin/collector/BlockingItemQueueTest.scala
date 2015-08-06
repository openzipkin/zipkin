package com.twitter.zipkin.collector

import com.twitter.conversions.time._
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.util.{Await, Future}
import org.scalatest._

/**
 * Tests the BlockingItemQueue to make sure that it can store and consume elements even when adding
 * more elements than what its initial capacity is
 */
class BlockingItemQueueTest extends FunSuite {

  val Item = ()

  def fill(queue: BlockingItemQueue[Unit, Unit], items: Int): Future[Boolean] = {
    val results = (0 until items) map { _ =>
      queue.add(Item) transform { e => Future.value(e) }
    }
    Future.collect(results).map(_.forall(_.isReturn))
  }

  test("Sleeps on a max queue and waits for the worker to drain") {

    val stats:InMemoryStatsReceiver = new InMemoryStatsReceiver()
    val queue = new BlockingItemQueue[Unit, Unit](10, 1, fallingBehindWorker, 100.millis,
                                                  100.millis, stats)
    // Add 11 and not 10 because the first one that's going to be added will be consumed right away
    assert(Await.result(fill(queue, 11)))
    assert(Await.ready(queue.add(Item)).poll.get.isReturn)
    Await.ready(queue.close())
    assert(stats.counter("queueFull").apply() >= 1)
    assert(stats.counter("successes").apply() == 12)
    assert(queue.size() == 0)
  }

  def fallingBehindWorker(param: Unit): Future[Unit] = {
    Future { Thread.sleep(100)
              param
           }
  }

}
