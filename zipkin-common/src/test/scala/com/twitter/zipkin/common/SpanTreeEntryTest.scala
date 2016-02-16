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
package com.twitter.zipkin.common

import com.twitter.zipkin.Constants
import org.scalatest.FunSuite
import scala.collection.mutable

class SpanTreeEntryTest extends FunSuite {
  // TODO these don't actually make any sense
  val annotations1 = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
    Annotation(150, Constants.ClientRecv, Some(Endpoint(456, 456, "service1"))))
  val annotations2 = List(Annotation(200, Constants.ClientSend, Some(Endpoint(456, 456, "service2"))),
    Annotation(250, Constants.ClientRecv, Some(Endpoint(123, 123, "service2"))))
  val annotations3 = List(Annotation(300, Constants.ClientSend, Some(Endpoint(456, 456, "service2"))),
    Annotation(350, Constants.ClientRecv, Some(Endpoint(666, 666, "service2"))))
  val annotations4 = List(Annotation(400, Constants.ClientSend, Some(Endpoint(777, 777, "service3"))),
    Annotation(500, Constants.ClientRecv, Some(Endpoint(888, 888, "service3"))))

  val span1Id = 666L
  val span2Id = 777L
  val span3Id = 888L
  val span4Id = 999L

  val span1 = Span(12345, "methodcall1", span1Id, None, None, None, annotations1)
  val span2 = Span(12345, "methodcall2", span2Id, Some(span1Id), None, None, annotations2)
  val span3 = Span(12345, "methodcall2", span3Id, Some(span2Id), None, None, annotations3)
  val span4 = Span(12345, "methodcall2", span4Id, Some(span3Id), None, None, annotations4)
  val span5 = Span(12345, "methodcall4", 1111L, Some(span4Id)) // no annotations

  val trace = List(span1, span2, span3, span4)

  test("create") {
    val spanTreeEntry4 = SpanTreeEntry(span4, List[SpanTreeEntry]())
    val spanTreeEntry3 = SpanTreeEntry(span3, List[SpanTreeEntry](spanTreeEntry4))
    val spanTreeEntry2 = SpanTreeEntry(span2, List[SpanTreeEntry](spanTreeEntry3))
    val spanTreeEntry1 = SpanTreeEntry(span1, List[SpanTreeEntry](spanTreeEntry2))
    assert(SpanTreeEntry.create(trace(0), trace) === spanTreeEntry1)
  }

  test("indexByParentId") {
    val span1 = Span(1, "a", 1)
    val span2 = Span(1, "b", 2, Some(1L))
    val span3 = Span(1, "c", 3, Some(2L))
    val span4 = Span(1, "d", 4, Some(2L))
    val map = new mutable.HashMap[Long, mutable.Set[Span]] with mutable.MultiMap[Long, Span]
    map.addBinding(1, span2)
    map.addBinding(2, span3)
    map.addBinding(2, span4)
    assert(SpanTreeEntry.indexByParentId(List(span1, span2, span3, span4)) === map)
  }
}
