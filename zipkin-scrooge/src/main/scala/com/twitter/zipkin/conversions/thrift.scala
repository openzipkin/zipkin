package com.twitter.zipkin.conversions

import com.twitter.conversions.time._
import com.twitter.zipkin.common._
import com.twitter.zipkin.gen
import com.twitter.zipkin.common.BinaryAnnotation
import com.twitter.zipkin.common.Annotation

object thrift {
  /* Endpoint */
  class ThriftEndpoint(e: Endpoint) {
    def toThrift = gen.Endpoint(e.ipv4, e.port, e.serviceName)
  }
  class WrappedEndpoint(e: gen.Endpoint) {
    def toEndpoint = {
      val serviceName = e.serviceName match {
        case (null | "") => Endpoint.UnknownServiceName
        case _ => e.serviceName
      }
      new Endpoint(e.ipv4, e.port, serviceName)
    }
  }
  implicit def endpointToThriftEndpoint(e: Endpoint) = new ThriftEndpoint(e)
  implicit def thriftEndpointToEndpoint(e: gen.Endpoint) = new WrappedEndpoint(e)

  /* AnnotationType */
  class ThriftAnnotationType(a: AnnotationType) {
    def toThrift = gen.AnnotationType(a.value)
  }
  class WrappedAnnotationType(a: gen.AnnotationType) {
    def toAnnotationType = AnnotationType(a.value, a.name)
  }
  implicit def annotationTypeToThriftAnnotationType(a: AnnotationType) = new ThriftAnnotationType(a)
  implicit def thriftAnnotationTypeToAnnotationType(a: gen.AnnotationType) = new WrappedAnnotationType(a)

  /* Annotation */
  class ThriftAnnotation(a: Annotation) {
    def toThrift = {
      gen.Annotation(a.timestamp, a.value, a.host.map { _.toThrift }, a.duration.map(_.inMicroseconds.toInt))
    }
  }
  class WrappedAnnotation(a: gen.Annotation) {
    def toAnnotation = {
      if (a.timestamp <= 0)
        throw new IllegalArgumentException("Annotation must have a timestamp: %s".format(a.toString))

      if ("".equals(a.value))
        throw new IllegalArgumentException("Annotation must have a value: %s".format(a.toString))

      new Annotation(a.timestamp, a.value, a.host.map { _.toEndpoint }, a.duration.map { _.microseconds })
    }
  }
  implicit def annotationToThriftAnnotation(a: Annotation) = new ThriftAnnotation(a)
  implicit def thriftAnnotationToAnnotation(a: gen.Annotation) = new WrappedAnnotation(a)

  /* BinaryAnnotation */
  class ThriftBinaryAnnotation(b: BinaryAnnotation) {
    def toThrift = {
      gen.BinaryAnnotation(b.key, b.value, b.annotationType.toThrift, b.host.map { _.toThrift })
    }
  }
  class WrappedBinaryAnnotation(b: gen.BinaryAnnotation) {
    def toBinaryAnnotation = {
      BinaryAnnotation(b.key, b.value, b.annotationType.toAnnotationType, b.host.map { _.toEndpoint })
    }
  }
  implicit def binaryAnnotationToThriftBinaryAnnotation(b: BinaryAnnotation) = new ThriftBinaryAnnotation(b)
  implicit def thriftBinaryAnnotationToBinaryAnnotation(b: gen.BinaryAnnotation) = new WrappedBinaryAnnotation(b)

  /* Span */
  class ThriftSpan(s: Span) {
    def toThrift = {
      gen.Span(s.traceId, s.name, s.id, s.parentId, s.annotations.map { _.toThrift },
        s.binaryAnnotations.map { _.toThrift }, s.debug)
    }
  }
  class WrappedSpan(s: gen.Span) {
    def toSpan = {
      s.name match {
        case null => throw new IncompleteTraceDataException("No name set in Span")
        case _ => ()
      }

      val annotations = s.annotations match {
        case null => List.empty[Annotation]
        case as => as.map { _.toAnnotation }.toList
      }

      val binaryAnnotations = s.binaryAnnotations match {
        case null => List.empty[BinaryAnnotation]
        case b => b.map { _.toBinaryAnnotation }
      }

      new Span(s.traceId, s.name, s.id, s.parentId, annotations, binaryAnnotations, s.debug)
    }
  }
  implicit def spanToThriftSpan(s: Span) = new ThriftSpan(s)
  implicit def thriftSpanToSpan(s: gen.Span) = new WrappedSpan(s)
}