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
package com.twitter.zipkin.adapter

import java.nio.ByteBuffer

import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.QueryRequest
import com.twitter.zipkin.thriftscala
import org.scalatest.FunSuite

class ThriftConversionsTest extends FunSuite {
  test("convert Annotation") {
    val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")))
    assert(expectedAnn.toThrift.toAnnotation === expectedAnn)

    val expectedAnn2: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")))
    assert(expectedAnn2.toThrift.toAnnotation === expectedAnn2)
  }

  test("convert AnnotationType") {
    val types = Seq("Bool", "Bytes", "I16", "I32", "I64", "Double", "String")
    types.zipWithIndex.foreach { case (value: String, index: Int) =>
      val expectedAnnType: AnnotationType = AnnotationType(index, value)
      assert(expectedAnnType.toThrift.toAnnotationType === expectedAnnType)
    }
  }

  test("convert BinaryAnnotation") {
    val expectedAnnType = AnnotationType(3, "I32")
    val expectedHost = Some(Endpoint(123, 456, "service"))
    val expectedBA: BinaryAnnotation =
      BinaryAnnotation("something", ByteBuffer.wrap("else".getBytes), expectedAnnType, expectedHost)
    assert(expectedBA.toThrift.toBinaryAnnotation === expectedBA)
  }

  test("convert Endpoint") {
    val expectedEndpoint: Endpoint = Endpoint(123, 456, "service")
    assert(expectedEndpoint.toThrift.toEndpoint === expectedEndpoint)

    // TODO this could happen if we deserialize an old style struct
    val actualEndpoint = thriftscala.Endpoint(123, 456, null)
    val expectedEndpoint2 = Endpoint(123, 456, Endpoint.UnknownServiceName)
    assert(actualEndpoint.toEndpoint === expectedEndpoint2)

    val mixedCaseEndpoint = thriftscala.Endpoint(123, 456, "SeRvIcE")
    assert(mixedCaseEndpoint.toEndpoint === Endpoint(123, 456, "service"))
  }

  test("convert Span") {
    val annotationValue = "NONSENSE"
    val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
    val expectedSpan = Span(12345, "methodcall", 666, None, List(expectedAnnotation))

    assert(expectedSpan.toThrift.toSpan === expectedSpan)

    val noNameSpan = thriftscala.Span(0, null, 0, None, Seq(), Seq())
    intercept[IncompleteTraceDataException] { noNameSpan.toSpan }

    val noAnnotationsSpan = thriftscala.Span(0, "name", 0, None, null, Seq())
    assert(noAnnotationsSpan.toSpan === Span(0, "name", 0))

    val mixedCaseSpan = thriftscala.Span(0, "NaMe", 0, None, null, Seq())
    assert(mixedCaseSpan.toSpan === Span(0, "name", 0))

    val noBinaryAnnotationsSpan = thriftscala.Span(0, "name", 0, None, Seq(), null)
    assert(noBinaryAnnotationsSpan.toSpan === Span(0, "name", 0))
  }

  test("thriftListToSpans doesn't allocate huge array") {
    assert(intercept[IllegalArgumentException] {
      thriftListToSpans("hello".getBytes())
    }.getMessage == "1701604463 > 10000: possibly malformed thrift")
  }
}
