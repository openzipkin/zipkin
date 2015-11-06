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

import com.twitter.finagle.CancelledRequestException
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.logging.{BareFormatter, Logger, StringHandler}
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.collector.QueueFullException
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{LogEntry, ResultCode, Span => ThriftSpan}
import org.scalatest.FunSuite
import java.util.concurrent.CancellationException

class ScribeSpanReceiverTest extends FunSuite {
  val serializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }
  val category = "zipkin"

  // Intentionally leaving timestamp and duration unset, as legacy instrumentation don't set this.
  val validSpan = Span(123, "boo", 456, annotations = List(new Annotation(1, "bah", None)))
  val validList = List(LogEntry(category, serializer.toString(validSpan.toThrift)))

  val base64 = "CgABAAAAAAAAAHsLAAMAAAADYm9vCgAEAAAAAAAAAcgPAAYMAAAAAQoAAQAAAAAAAAABCwACAAAAA2JhaAAPAAgMAAAAAAIACQAA"

  test("processes entries") {
    var recvdSpan: Option[Seq[ThriftSpan]] = None
    val receiver = new ScribeReceiver(Set(category), { s =>
      recvdSpan = Some(s)
      Future.Done
    })
    assert(Await.result(receiver.log(Seq(validList.head, validList.head))) === ResultCode.Ok)
    assert(!recvdSpan.isEmpty)
    assert(recvdSpan.get.map(_.toSpan) === Seq(validSpan, validSpan))
  }

  test("ok when scribe client cancels their request") {
    val cancelled = new CancellationException()
    cancelled.initCause(new CancelledRequestException())
    val receiver = new ScribeReceiver(Set(category), { _ => Future.exception(cancelled) })
    assert(Await.result(receiver.log(validList)) === ResultCode.Ok)
  }

  test("pushes back on QueueFullException, but doesn't log or increment errors") {
    val stats = new InMemoryStatsReceiver()
    val log = logHandle(classOf[ScribeReceiver])

    val receiver = new ScribeReceiver(Set(category), { _ => Future.exception(new QueueFullException(1)) }, stats)
    assert(Await.result(receiver.log(validList)) === ResultCode.TryLater)
    assert(stats.counters(List("pushBack")) === 1)
    assert(!stats.counters.contains(List("processingError", classOf[QueueFullException].getName)))
    assert(log.get.trim === "")
  }

  test("logs and increments processingError on Exception with message") {
    val stats = new InMemoryStatsReceiver()
    val log = logHandle(classOf[ScribeReceiver])

    val receiver = new ScribeReceiver(Set(category), { _ =>
      Future.exception(new NullPointerException("foo was null")) }, stats)
    assert(Await.result(receiver.log(validList)) === ResultCode.TryLater)
    assert(stats.counters(List("processingError", classOf[NullPointerException].getName)) === 1)
    assert(log.get.trim === "Sending TryLater due to NullPointerException(foo was null)")
  }

  test("don't print null when exception hasn't any message") {
    val log = logHandle(classOf[ScribeReceiver])

    val receiver = new ScribeReceiver(Set(category), { _ => Future.exception(new Exception) })
    Await.result(receiver.log(validList))
    assert(log.get.trim === "Sending TryLater due to Exception()")
  }

  def logHandle(clazz: Class[_]): StringHandler = {
    val handler = new StringHandler(BareFormatter, None)
    val logger = Logger.get(classOf[ScribeReceiver])
    logger.clearHandlers()
    logger.addHandler(handler)
    handler
  }

  test("ignores bad categories") {
    var recvdSpan: Option[ThriftSpan] = None
    val receiver = new ScribeReceiver(Set("othercat"), { s =>
      recvdSpan = Some(s.head)
      Future.Done
    })
    assert(Await.result(receiver.log(validList)) === ResultCode.Ok)
    assert(recvdSpan.isEmpty)
  }

  test("ignores bad messages") {
    var recvdSpan: Option[ThriftSpan] = None
    val receiver = new ScribeReceiver(Set(category), { s =>
      recvdSpan = Some(s.head)
      Future.Done
    })
    assert(Await.result(receiver.log(Seq(LogEntry(category, "badencoding")))) === ResultCode.Ok)
    assert(recvdSpan.isEmpty)
  }
}
