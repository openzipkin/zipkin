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
package com.twitter.zipkin.storm

import com.twitter.zipkin.common.{Endpoint, Annotation, Span}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SerializationTest extends FunSuite {
  val annotation1 = Annotation(1, "cs", Some(Endpoint(1, 2, "service1")))
  val annotation2 = Annotation(2, "cr", Some(Endpoint(3, 4, "Service2")))
  val annotation3 = Annotation(3, "cr", Some(Endpoint(5, 6, "Service3")))
  val spanParent = Span(12345, "methodcall", 666, Some(777),
    List(annotation1), Nil)
  val spanParentDup = Span(12345, "methodcall", 666, Some(777),
    List(annotation2), Nil)
  val spanChild = Span(12345, "methodcall", 888, Some(666),
    List(annotation3), Nil)

  val zero = Span(0, "zero", 0, None, Nil, Nil, true)
  val invalid = Span(0, "invalid", 0, None, Nil, Nil, true)

  test("spanMonoid plus should merge dup span") {
    val expectedSpan = Span(12345, "methodcall", 666, Some(777),
      List(annotation1, annotation2), Nil)
    val spanAfterPlus = Serialization.spanMonoid.plus(spanParent, spanParentDup)
    assert(expectedSpan === spanAfterPlus)
  }

  test("spanMonoid plus parent and child span should be invalid") {
    val spanAfterPlus = Serialization.spanMonoid.plus(spanParent, spanChild)
    assert(invalid === spanAfterPlus)
  }

  test("spanMonoid plus zero and span should be span") {
    val spanAfterPlus = Serialization.spanMonoid.plus(spanParent, zero)
    assert(spanAfterPlus === spanAfterPlus)
  }

  test("spanMonoid plus invalid and span should be invalid") {
    val spanAfterPlus = Serialization.spanMonoid.plus(invalid, spanParent)
    assert(invalid === spanAfterPlus)
  }

  test("map[string, long] injection") {
    val mInj = Serialization.mapStrLongInj
    val map = Map("str1" -> 1L, "str2" -> 2L)
    val value = mInj.invert(mInj(map)).get
    assert(map === value)
  }

  test("map[string, list[long]] injection") {
    val mInj = Serialization.mapStrListInj
    val map = Map("str1" -> List(1L), "str2" -> List(2L))
    val value = mInj.invert(mInj(map)).get
    assert(map === value)
  }
}
