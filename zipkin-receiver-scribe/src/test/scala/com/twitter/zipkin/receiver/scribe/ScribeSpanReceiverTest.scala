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

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.{LogEntry, ResultCode, Span => ThriftSpan}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ScribeSpanReceiverTest extends FunSuite {
  val serializer = new BinaryThriftStructSerializer[ThriftSpan] {
    def codec = ThriftSpan
  }
  val category = "zipkin"

  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
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

  test("pushes back") {
    val receiver = new ScribeReceiver(Set(category), { _ => Future.exception(new Exception) })
    assert(Await.result(receiver.log(validList)) === ResultCode.TryLater)
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
