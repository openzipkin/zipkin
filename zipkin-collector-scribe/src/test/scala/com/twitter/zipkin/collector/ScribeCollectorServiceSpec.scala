/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.collector

import com.twitter.algebird.Moments
import com.twitter.conversions.time._
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Await, Future, Time}
import com.twitter.zipkin.common.{Annotation, Service, _}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.{Aggregates, Store}
import com.twitter.zipkin.thriftscala
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers, OneInstancePerTest}

class ScribeCollectorServiceSpec extends FunSuite with OneInstancePerTest with Matchers with MockitoSugar {
  val serializer = new BinaryThriftStructSerializer[thriftscala.Span] {
    def codec = thriftscala.Span
  }
  val category = "zipkin"
  val serviceName = "mockingbird"
  val annotations = Seq("a" , "b", "c")

  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
  val validList = List(thriftscala.LogEntry(category, serializer.toString(validSpan.toThrift)))

  val wrongCatList = List(thriftscala.LogEntry("wrongcat", serializer.toString(validSpan.toThrift)))

  val base64 = "CgABAAAAAAAAAHsLAAMAAAADYm9vCgAEAAAAAAAAAcgPAAYMAAAAAQoAAQAAAAAAAAABCwACAAAAA2JhaAAPAAgMAAAAAAIACQAA"

  val queue = mock[WriteQueue[Seq[String]]]
  val mockAggregates = mock[Aggregates]

  def cs = new ScribeCollectorService(queue, Seq(Store(null, mockAggregates)), Set(category)) {
    running = true
  }

  test("add to queue") {
    when(queue.add(List(base64))) thenReturn true

    Await.result(cs.log(validList)) should be (thriftscala.ResultCode.Ok)
  }

  test("push back") {
    when(queue.add(List(base64))) thenReturn false

    Await.result(cs.log(validList)) should be (thriftscala.ResultCode.TryLater)
  }

  test("ignore wrong category") {
    Await.result(cs.log(wrongCatList)) should be (thriftscala.ResultCode.Ok)

    verify(queue, never()).add(anyObject())
  }

  test("store dependencies") {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

    when(mockAggregates.storeDependencies(deps1)) thenReturn Future.Done

    cs.storeDependencies(deps1.toThrift)

    verify(mockAggregates).storeDependencies(deps1)
  }
}
