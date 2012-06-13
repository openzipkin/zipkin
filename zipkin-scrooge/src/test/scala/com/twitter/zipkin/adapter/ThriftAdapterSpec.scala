package com.twitter.zipkin.adapter

import com.twitter.zipkin.gen
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import com.twitter.zipkin.common.{IncompleteTraceDataException, Span, Endpoint, Annotation}

class ThriftAdapterSpec extends Specification with JMocker with ClassMocker {

  "ThriftAdapter" should {
    "convert Annotation" in {
      "to thrift and back" in {
        val expectedAnn: Annotation = Annotation(123, "value", Some(Endpoint(123, 123, "service")))
        val thriftAnn: gen.Annotation = ThriftAdapter(expectedAnn)
        val actualAnn: Annotation = ThriftAdapter(thriftAnn)
        expectedAnn mustEqual actualAnn
      }
    }

    "convert AnnotationType" in {
      "to thrift and back" in {

      }
    }

    "convert BinaryAnnotation" in {
      "to thrift and back" in {

      }
    }

    "convert Endpoint" in {
      "to thrift and back" in {
        val expectedEndpoint: Endpoint = Endpoint(123, 456, "service")
        val thriftEndpoint: gen.Endpoint = ThriftAdapter(expectedEndpoint)
        val actualEndpoint: Endpoint = ThriftAdapter(thriftEndpoint)
        expectedEndpoint mustEqual actualEndpoint
      }

      "to thrift and back, with null service" in {
        // TODO this could happen if we deserialize an old style struct
        val actualEndpoint = ThriftAdapter(gen.Endpoint(123, 456, null))
        val expectedEndpoint = Endpoint(123, 456, Endpoint.UnknownServiceName)
        expectedEndpoint mustEqual actualEndpoint
      }
    }

    "convert Span" in {
      val annotationValue = "NONSENSE"
      val expectedAnnotation = Annotation(1, annotationValue, Some(Endpoint(1, 2, "service")))
      val expectedSpan = Span(12345, "methodcall", 666, None,
        List(expectedAnnotation), Nil)

      "to thrift and back" in {
        val thriftSpan: gen.Span = ThriftAdapter(expectedSpan)
        val actualSpan: Span = ThriftAdapter(thriftSpan)
        expectedSpan mustEqual actualSpan
      }

      "handle incomplete thrift span" in {
        val noNameSpan = gen.Span(0, null, 0, None, Seq(), Seq())
        ThriftAdapter(noNameSpan) must throwA[IncompleteTraceDataException]

        val noAnnotationsSpan = gen.Span(0, "name", 0, None, null, Seq())
        ThriftAdapter(noAnnotationsSpan) mustEqual Span(0, "name", 0, None, List(), Seq())

        val noBinaryAnnotationsSpan = gen.Span(0, "name", 0, None, Seq(), null)
        ThriftAdapter(noBinaryAnnotationsSpan) mustEqual Span(0, "name", 0, None, List(), Seq())
      }
    }
  }

}
