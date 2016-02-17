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

class SpanTest extends FunSuite {
  val span = Span(12345, "methodcall", 666, None, None, None,
    List(
      Annotation(1, Constants.ClientSend, Some(Endpoint(1, 2, "instrumented_client"))),
      Annotation(1, Constants.ServerRecv, Some(Endpoint(1, 2, "instrumented_server"))),
      Annotation(1, Constants.ServerSend, Some(Endpoint(1, 2, "instrumented_server"))),
      Annotation(1, Constants.ClientRecv, Some(Endpoint(1, 2, "instrumented_client")))
    ),
    List(
      BinaryAnnotation(Constants.ClientAddr, true, Some(Endpoint(1, 2, "client_label"))),
      BinaryAnnotation(Constants.ServerAddr, true, Some(Endpoint(1, 2, "server_label"))),
      BinaryAnnotation(Constants.LocalComponent, "foo", Some(Endpoint(1, 2, "instrumented_local")))
    ))
  val spanWithEmptyServiceNames = Span(12345, "methodcall", 666, None, None, None,
    List(
      Annotation(1, Constants.ClientSend, Some(Endpoint(1, 2, "instrumented_client"))),
      Annotation(1, Constants.ServerRecv, Some(Endpoint(1, 2, "instrumented_server"))),
      Annotation(1, Constants.ServerSend, Some(Endpoint(1, 2, "instrumented_server"))),
      Annotation(1, Constants.ClientRecv, Some(Endpoint(1, 2, "instrumented_client")))
    ),
    List(
      BinaryAnnotation(Constants.ClientAddr, true, Some(Endpoint(1, 2, ""))), // empty service name
      BinaryAnnotation(Constants.ServerAddr, true, Some(Endpoint(1, 2, ""))) // empty service name
    ))

  /** Representations should lowercase on the way in */
  test("name cannot be lowercase") {
    intercept[IllegalArgumentException] {
      Span(12345, "Foo", 666)
    }
  }

  test("endpoints include binary annotations") {
    assert(span.endpoints.map(_.serviceName) === Set("server_label", "client_label", "instrumented_server", "instrumented_client", "instrumented_local"))
  }

  test("serviceNames include binary annotations") {
    assert(span.serviceNames === Set("server_label", "client_label", "instrumented_server", "instrumented_client", "instrumented_local"))
  }

  test("serviceNames ignores annotations with empty service names") {
    // endpoints do return empty service name
    assert(spanWithEmptyServiceNames.endpoints.map(_.serviceName) === Set("instrumented_client", "instrumented_server", ""))
    // but serviceNames method does not
    assert(spanWithEmptyServiceNames.serviceNames === Set("instrumented_client", "instrumented_server"))
  }

  test("serviceName preference") {
    var span = this.span

    // Most authoritative is the label of the server's endpoint
    assert(span.serviceName === Some("server_label"))

    span = span.copy(binaryAnnotations = List(span.binaryAnnotations(0)))

    // Next, the label of any server annotation, logged by an instrumented server
    assert(span.serviceName === Some("instrumented_server"))

    // Next is the label of the client's endpoint
    span = span.copy(annotations = List(span.annotations(0), span.annotations(3)))

    assert(span.serviceName === Some("client_label"))

    // Next is the label of any client annotation, logged by an instrumented client
    span = span.copy(binaryAnnotations = List.empty)

    assert(span.serviceName === Some("instrumented_client"))

    // Finally is the label of the local component's endpoint
    span = span.copy(annotations = List.empty, binaryAnnotations = List(this.span.binaryAnnotations.last))

    assert(span.serviceName === Some("instrumented_local"))
  }

  test("serviceName ignores annotations with empty service names") {
    // ServerAddress annotation's serviceName is empty, so should be ignored in favor of sr/ss annotations
    assert(spanWithEmptyServiceNames.serviceName === Some("instrumented_server"))
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
