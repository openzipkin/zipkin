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
import org.scalatest.FunSuite

class SpanTest extends FunSuite {

  val annotationValue = "NONSENSE"
  val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
  val expectedSpan = Span(12345, "methodcall", 666, None, List(expectedAnnotation))

  val annotation1 = Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
  val annotation2 = Annotation(2, "value2", Some(Endpoint(3, 4, "service")))
  val annotation3 = Annotation(3, "value3", Some(Endpoint(5, 6, "service")))

  val binaryAnnotation1 = BinaryAnnotation("key1", ByteBuffer.wrap("value1".getBytes), AnnotationType.String, Some(Endpoint(1, 2, "service1")))
  val binaryAnnotation2 = BinaryAnnotation("key2", ByteBuffer.wrap("value2".getBytes), AnnotationType.String, Some(Endpoint(3, 4, "service2")))

  val spanWith3Annotations = Span(12345, "methodcall", 666, None,
    List(annotation1, annotation2, annotation3))
  val spanWith2BinaryAnnotations = Span(12345, "methodcall", 666, None,
    List.empty, Seq(binaryAnnotation1, binaryAnnotation2))

  /** Representations should lowercase on the way in */
  test("name cannot be lowercase") {
    intercept[IllegalArgumentException] {
      Span(12345, "Foo", 666)
    }
  }

  test("serviceName preference") {
    var span = Span(12345, "methodcall", 666, None,
      List(
        Annotation(1, "cs", Some(Endpoint(1, 2, "cs"))),
        Annotation(1, "sr", Some(Endpoint(1, 2, "sr"))),
        Annotation(1, "ss", Some(Endpoint(1, 2, "ss"))),
        Annotation(1, "cr", Some(Endpoint(1, 2, "cr")))
      ),
      List(
        BinaryAnnotation("ca", BinaryAnnotationValue(true), Some(Endpoint(1, 2, "ca"))),
        BinaryAnnotation("sa", BinaryAnnotationValue(true), Some(Endpoint(1, 2, "sa")))
      ))

    // Most authoritative is the label of the server's endpoint
    assert(span.serviceName === Some("sa"))

    span = span.copy(binaryAnnotations = List(span.binaryAnnotations(0)))

    // Next, the label of any server annotation, logged by an instrumented server
    assert(span.serviceName === Some("sr"))

    // Next is the label of the client's endpoint
    span = span.copy(annotations = List(span.annotations(0), span.annotations(3)))

    assert(span.serviceName === Some("ca"))

    // Finally, the label of any client annotation, logged by an instrumented client
    span = span.copy(binaryAnnotations = List.empty)

    assert(span.serviceName === Some("cs"))
  }

  test("merge two span parts") {
    val ann1 = Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
    val ann2 = Annotation(2, "value2", Some(Endpoint(3, 4, "service")))

    val span1 = Span(12345, "", 666, None, List(ann1), Seq(), Some(true))
    val span2 = Span(12345, "methodcall", 666, None, List(ann2), Seq(), Some(false))
    val expectedSpan = Span(12345, "methodcall", 666, None, List(ann1, ann2), Seq(), Some(true))
    val actualSpan = span1.mergeSpan(span2)
    assert(actualSpan === expectedSpan)
  }

  test("merge span with Unknown span name with known span name") {
    val span1 = Span(1, "unknown", 2)
    val span2 = Span(1, "get", 2)

    assert(span1.mergeSpan(span2).name === "get")
    assert(span2.mergeSpan(span1).name === "get")
  }

  test("return the first annotation") {
    assert(spanWith3Annotations.firstAnnotation.get === annotation1)
  }

  test("return the last annotation") {
    assert(spanWith3Annotations.lastAnnotation.get === annotation3)
  }

  test("get duration") {
    assert(spanWith3Annotations.duration === Some(2))
  }

  test("don't get duration duration when there are no annotations") {
    val span = Span(1, "n", 2)
    assert(span.duration === None)
  }
}
