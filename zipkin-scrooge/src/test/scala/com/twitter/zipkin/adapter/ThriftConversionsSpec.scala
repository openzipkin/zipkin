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

import com.twitter.conversions.time._
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.query._
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import java.nio.ByteBuffer

class ThriftConversionsSpec extends Specification with JMocker with ClassMocker {

  "ThriftConversions" should {
    "convert Annotation" in {
      "to thrift and back" in {
        val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")))
        expectedAnn.toThrift.toAnnotation mustEqual expectedAnn
      }
      "to thrift and back, with duration" in {
        val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")), Some(1.seconds))
        expectedAnn.toThrift.toAnnotation mustEqual expectedAnn
      }
    }

    "convert AnnotationType" in {
      val types = Seq("Bool", "Bytes", "I16", "I32", "I64", "Double", "String")
      "to thrift and back" in {
        types.zipWithIndex.foreach { case (value: String, index: Int) =>
          val expectedAnnType: AnnotationType = AnnotationType(index, value)
          expectedAnnType.toThrift.toAnnotationType mustEqual expectedAnnType
        }
      }
    }

    "convert BinaryAnnotation" in {
      "to thrift and back" in {
        val expectedAnnType = AnnotationType(3, "I32")
        val expectedHost = Some(Endpoint(123, 456, "service"))
        val expectedBA: BinaryAnnotation =
          BinaryAnnotation("something", ByteBuffer.wrap("else".getBytes), expectedAnnType, expectedHost)
        expectedBA.toThrift.toBinaryAnnotation mustEqual expectedBA
      }
    }

    "convert Endpoint" in {
      "to thrift and back" in {
        val expectedEndpoint: Endpoint = Endpoint(123, 456, "service")
        expectedEndpoint.toThrift.toEndpoint mustEqual expectedEndpoint
      }

      "to thrift and back, with null service" in {
        // TODO this could happen if we deserialize an old style struct
        val actualEndpoint = gen.Endpoint(123, 456, null)
        val expectedEndpoint = Endpoint(123, 456, Endpoint.UnknownServiceName)
        actualEndpoint.toEndpoint mustEqual expectedEndpoint
      }
    }

    "convert Span" in {
      val annotationValue = "NONSENSE"
      val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
      val expectedSpan = Span(12345, "methodcall", 666, None,
        List(expectedAnnotation), Nil)

      "to thrift and back" in {
        expectedSpan.toThrift.toSpan mustEqual expectedSpan
      }

      "handle incomplete thrift span" in {
        val noNameSpan = gen.Span(0, null, 0, None, Seq(), Seq())
        noNameSpan.toSpan must throwA[IncompleteTraceDataException]

        val noAnnotationsSpan = gen.Span(0, "name", 0, None, null, Seq())
        noAnnotationsSpan.toSpan mustEqual Span(0, "name", 0, None, List(), Seq())

        val noBinaryAnnotationsSpan = gen.Span(0, "name", 0, None, Seq(), null)
        noBinaryAnnotationsSpan.toSpan mustEqual Span(0, "name", 0, None, List(), Seq())
      }
    }

    "convert Trace" in {
      "to thrift and back" in {
        val span = Span(12345, "methodcall", 666, None,
          List(Annotation(1, "boaoo", None)), Nil)
        val expectedTrace = Trace(List[Span](span))
        val thriftTrace = expectedTrace.toThrift
        val actualTrace = thriftTrace.toTrace
        expectedTrace mustEqual actualTrace
      }
    }

    "convert TraceSummary" in {
      "to thrift and back" in {
        val expectedTraceSummary = TraceSummary(123, 10000, 10300, 300, Map("service1" -> 1),
          List(Endpoint(123, 123, "service1")))
        val thriftTraceSummary = expectedTraceSummary.toThrift
        val actualTraceSummary = thriftTraceSummary.toTraceSummary
        expectedTraceSummary mustEqual actualTraceSummary
      }
    }
  }
}
