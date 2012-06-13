package com.twitter.zipkin.adapter

import com.twitter.zipkin.gen
import com.twitter.zipkin.common._

object ThriftAdapter extends Adapter {
  type annotationType = gen.Annotation
  type annotationTypeType = gen.AnnotationType
  type binaryAnnotationType = gen.BinaryAnnotation
  type endpointType = gen.Endpoint
  type spanType = gen.Span

  /* Annotation from Thrift */
  def apply(a: annotationType): Annotation = {
    if (a.timestamp <= 0)
      throw new IllegalArgumentException("Annotation must have a timestamp: %s".format(a.toString))

    if ("".equals(a.value))
      throw new IllegalArgumentException("Annotation must have a value: %s".format(a.toString))

    new Annotation(a.timestamp, a.value, a.host.map { this(_) })
  }

  /* Annotation to Thrift */
  def apply(a: Annotation): annotationType = {
    gen.Annotation(a.timestamp, a.value, a.host.map { this(_) })
  }

  /* AnnotationType from Thrift */
  def apply(a: annotationTypeType): AnnotationType = {
    AnnotationType(a.value, a.name)
  }

  /* AnnotationType to Thrift */
  def apply(a: AnnotationType): annotationTypeType = {
    gen.AnnotationType(a.value)
  }

  /* BinaryAnnotation from Thrift */
  def apply(b: binaryAnnotationType): BinaryAnnotation = {
    BinaryAnnotation(
      b.`key`,
      b.`value`,
      this(b.`annotationType`),
      b.`host`.map { this(_) }
    )
  }

  /* BinaryAnnotation to Thrift */
  def apply(b: BinaryAnnotation): binaryAnnotationType = {
    gen.BinaryAnnotation(
      b.key,
      b.value,
      this(b.annotationType),
      b.host.map { this(_) }
    )
  }

  /* Endpoint from Thrift */
  def apply(e: endpointType): Endpoint = {
    val serviceName = e.serviceName match {
      case null => Endpoint.UnknownServiceName
      case "" => Endpoint.UnknownServiceName
      case _ => e.serviceName
    }

    new Endpoint(e.ipv4, e.port, serviceName)
  }

  /* Endpoint to Thrift */
  def apply(e: Endpoint): endpointType = {
    gen.Endpoint(e.ipv4, e.port, e.serviceName)
  }

  /* Span from Thrift */
  def apply(s: spanType): Span = {
    s.`name` match {
      case null => throw new IncompleteTraceDataException("No name set in Span")
      case _ => ()
    }

    val annotations = s.annotations match {
      case null => List[Annotation]()
      case as => as.map { this(_) }.toList
    }

    val binaryAnnotations = s.binaryAnnotations match {
      case null => Seq[BinaryAnnotation]()
      case b => b.map { this(_) }
    }

    new Span(s.traceId, s.name, s.id, s.parentId, annotations, binaryAnnotations)
  }

  /* Span to Thrift */
  def apply(s: Span): spanType = {
    gen.Span(s.traceId, s.name, s.id, s.parentId, s.annotations.map { this(_) },
      s.binaryAnnotations.map { this(_) })
  }
}
