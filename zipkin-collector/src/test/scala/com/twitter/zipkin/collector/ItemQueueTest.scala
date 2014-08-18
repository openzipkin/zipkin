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

import com.twitter.util.{Await, Future}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ItemQueueTest extends FunSuite {
  val Item = ()

  def fill(queue: ItemQueue[Unit, Unit], items: Int): Future[Boolean] = {
    val results = (0 until items) map { _ =>
      queue.add(Item) transform { e => Future.value(e) }
    }
    Future.collect(results).map(_.forall(_.isReturn))
  }

  test("processes messages") {
    val processed = new CountDownLatch(5)

    val queue = new ItemQueue[Unit, Unit](6, 2, { _ =>
      processed.countDown()
      Future.Unit
    })

    assert(Await.result(fill(queue, 5)))
    assert(processed.await(500, TimeUnit.MILLISECONDS))
  }

  test("runs a specified number of concurrent workers") {
    val latch = new CountDownLatch(1)
    val processors = new CountDownLatch(5)
    val processed = new CountDownLatch(10)

    val queue = new ItemQueue[Unit, Unit](10, 5, { _ =>
      processed.countDown()
      processors.countDown()
      latch.await()
      Future.Unit
    })

    assert(Await.result(fill(queue, 10)))

    // pool size of 5 means 5 items should have been pulled from the queue
    assert(processors.await(100, TimeUnit.MILLISECONDS))
    assert(processed.getCount() === 5)

    // complete processing
    latch.countDown()
    assert(processed.await(100, TimeUnit.MILLISECONDS))
  }

  test("enforces a max queue size") {
    val queue = new ItemQueue[Unit, Unit](10, 0, { _ => Future.Unit })
    assert(Await.result(fill(queue, 10)))
    assert(Await.ready(queue.add(Item)).poll.get.isThrow)
  }

  test("wont accept items after being closed") {
    val queue = new ItemQueue[Unit, Unit](10, 5, { _ => Future.Unit })
    assert(Await.result(fill(queue, 5)))
    queue.close()
    assert(Await.ready(queue.add(Item)).poll.get.isThrow)
  }

  test("drains after being closed") {
    val latch = new CountDownLatch(1)
    val processed = new CountDownLatch(10)

    val queue = new ItemQueue[Unit, Unit](10, 5, { _ =>
      processed.countDown()
      latch.await()
      Future.Unit
    })

    assert(Await.result(fill(queue, 10)))

    queue.close()
    assert(Await.ready(queue.add(Item)).poll.get.isThrow)

    latch.countDown()
    assert(processed.await(100, TimeUnit.MILLISECONDS))
  }
}
