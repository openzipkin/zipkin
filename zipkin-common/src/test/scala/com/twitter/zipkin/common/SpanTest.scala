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

import java.nio.ByteBuffer

import com.twitter.zipkin.Constants
import org.scalatest.FunSuite

class SpanTest extends FunSuite {

  val annotationValue = "NONSENSE"
  val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
  val expectedSpan = Span(12345, "methodcall", 666, None,
    List(expectedAnnotation), Nil)

  val annotation1 = Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
  val annotation2 = Annotation(2, "value2", Some(Endpoint(3, 4, "Service"))) // upper case service name
  val annotation3 = Annotation(3, "value3", Some(Endpoint(5, 6, "service")))

  val binaryAnnotation1 = BinaryAnnotation("key1", ByteBuffer.wrap("value1".getBytes), AnnotationType.String, Some(Endpoint(1, 2, "service1")))
  val binaryAnnotation2 = BinaryAnnotation("key2", ByteBuffer.wrap("value2".getBytes), AnnotationType.String, Some(Endpoint(3, 4, "service2")))

  val spanWith3Annotations = Span(12345, "methodcall", 666, None,
    List(annotation1, annotation2, annotation3), Nil)
  val spanWith2BinaryAnnotations = Span(12345, "methodcall", 666, None,
    Nil, List(binaryAnnotation1, binaryAnnotation2))


  test("serviceNames is lowercase") {
    val names = spanWith3Annotations.serviceNames
    assert(names.size === 1)
    assert(names.toSeq(0) === "service")
  }

  test("serviceNames") {
    val map = expectedSpan.getAnnotationsAsMap
    val actualAnnotation = map.get(annotationValue).get
    assert(expectedAnnotation === actualAnnotation)
  }

  test("merge two span parts") {
    val ann1 = Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
    val ann2 = Annotation(2, "value2", Some(Endpoint(3, 4, "service")))

    val span1 = Span(12345, "", 666, None, List(ann1), Nil, true)
    val span2 = Span(12345, "methodcall", 666, None, List(ann2), Nil, false)
    val expectedSpan = Span(12345, "methodcall", 666, None, List(ann1, ann2), Nil, true)
    val actualSpan = span1.mergeSpan(span2)
    assert(actualSpan === expectedSpan)
  }

  test("merge span with Unknown span name with known span name") {
    val span1 = Span(1, "Unknown", 2, None, List(), Seq())
    val span2 = Span(1, "get", 2, None, List(), Seq())

    assert(span1.mergeSpan(span2).name === "get")
    assert(span2.mergeSpan(span1).name === "get")
  }

  test("return the first annotation") {
    assert(spanWith3Annotations.firstAnnotation.get === annotation1)
  }

  test("return the last annotation") {
    assert(spanWith3Annotations.lastAnnotation.get === annotation3)
  }

  test("know this is not a client side span") {
    val spanSr = Span(1, "n", 2, None, List(Annotation(1, Constants.ServerRecv, None)), Nil)
    assert(!spanSr.isClientSide)
  }

  test("get duration") {
    assert(spanWith3Annotations.duration === Some(2))
  }

  test("don't get duration duration when there are no annotations") {
    val span = Span(1, "n", 2, None, List(), Nil)
    assert(span.duration === None)
  }

  test("validate span") {
    val cs = Annotation(1, Constants.ClientSend, None)
    val sr = Annotation(2, Constants.ServerRecv, None)
    val ss = Annotation(3, Constants.ServerSend, None)
    val cr = Annotation(4, Constants.ClientRecv, None)

    val cs2 = Annotation(5, Constants.ClientSend, None)

    val s1 = Span(1, "i", 123, None, List(cs, sr, ss, cr), Nil)
    assert(s1.isValid)

    val s3 = Span(1, "i", 123, None, List(cs, sr, ss, cr, cs2), Nil)
    assert(!s3.isValid)
  }

  test("get binary annotation") {
    assert(spanWith2BinaryAnnotations.getBinaryAnnotation("key1") === Some(binaryAnnotation1))
    assert(spanWith2BinaryAnnotations.getBinaryAnnotation("NoExitingKey") === None)
  }
}
