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

import org.scalatest.FunSuite

class SpanTest extends FunSuite {
  val span = Span(12345, "methodcall", 666, None, None, None,
    List(
      Annotation(1, "cs", Some(Endpoint(1, 2, "cs"))),
      Annotation(1, "sr", Some(Endpoint(1, 2, "sr"))),
      Annotation(1, "ss", Some(Endpoint(1, 2, "ss"))),
      Annotation(1, "cr", Some(Endpoint(1, 2, "cr")))
    ),
    List(
      BinaryAnnotation("ca", true, Some(Endpoint(1, 2, "ca"))),
      BinaryAnnotation("sa", true, Some(Endpoint(1, 2, "sa"))),
      BinaryAnnotation("lc", "foo", Some(Endpoint(1, 2, "lc")))
    ))
  val spanWithEmptyServiceNames = Span(12345, "methodcall", 666, None, None, None,
    List(
      Annotation(1, "cs", Some(Endpoint(1, 2, "cli"))),
      Annotation(1, "sr", Some(Endpoint(1, 2, "srv"))),
      Annotation(1, "ss", Some(Endpoint(1, 2, "srv"))),
      Annotation(1, "cr", Some(Endpoint(1, 2, "cli")))
    ),
    List(
      BinaryAnnotation("ca", true, Some(Endpoint(1, 2, ""))), // empty service name
      BinaryAnnotation("sa", true, Some(Endpoint(1, 2, ""))) // empty service name
    ))

  /** Representations should lowercase on the way in */
  test("name cannot be lowercase") {
    intercept[IllegalArgumentException] {
      Span(12345, "Foo", 666)
    }
  }

  test("endpoints include binary annotations") {
    assert(span.endpoints.map(_.serviceName) === Set("cs", "cr", "ss", "sr", "ca", "sa", "lc"))
  }

  test("serviceNames include binary annotations") {
    assert(span.serviceNames === Set("cs", "cr", "ss", "sr", "ca", "sa", "lc"))
  }

  test("serviceNames ignores annotations with empty service names") {
    // endpoints do return empty service name
    assert(spanWithEmptyServiceNames.endpoints.map(_.serviceName) === Set("cli", "srv", ""))
    // but serviceNames method does not
    assert(spanWithEmptyServiceNames.serviceNames === Set("cli", "srv"))
  }

  test("serviceName preference") {
    var span = this.span

    // Most authoritative is the label of the server's endpoint
    assert(span.serviceName === Some("sa"))

    span = span.copy(binaryAnnotations = List(span.binaryAnnotations(0)))

    // Next, the label of any server annotation, logged by an instrumented server
    assert(span.serviceName === Some("sr"))

    // Next is the label of the client's endpoint
    span = span.copy(annotations = List(span.annotations(0), span.annotations(3)))

    assert(span.serviceName === Some("ca"))

    // Next is the label of any client annotation, logged by an instrumented client
    span = span.copy(binaryAnnotations = List.empty)

    assert(span.serviceName === Some("cs"))

    // Finally is the label of the local component's endpoint
    span = span.copy(binaryAnnotations = List(this.span.binaryAnnotations.last))

    assert(span.serviceName === Some("cs"))
  }

  test("serviceName ignores annotations with empty service names") {
    // ServerAddress annotation's serviceName is empty, so should be ignored in favor of sr/ss annotations
    assert(spanWithEmptyServiceNames.serviceName === Some("srv"))
  }

  test("merge two span parts") {
    val ann1 = Annotation(1, "value1", Some(Endpoint(1, 2, "service")))
    val ann2 = Annotation(2, "value2", Some(Endpoint(3, 4, "service")))

    val span1 = Span(12345, "", 666, None, Some(1), None, List(ann1), Seq(), Some(true))
    val span2 = Span(12345, "methodcall", 666, None, None, Some(2), List(ann2), Seq(), Some(false))
    val expectedSpan = Span(12345, "methodcall", 666, None, Some(1), Some(2), List(ann1, ann2), Seq(), Some(true))

    assert(span1.merge(span2) === expectedSpan)
    assert(span2.merge(span1) === expectedSpan)
  }

  test("merge span with Unknown span name with known span name") {
    val span1 = Span(1, "unknown", 2)
    val span2 = Span(1, "get", 2)

    assert(span1.merge(span2).name === "get")
    assert(span2.merge(span1).name === "get")
  }
}
