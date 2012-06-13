package com.twitter.zipkin.adapter

import com.twitter.zipkin.common._

trait Adapter {
  type annotationType
  type annotationTypeType
  type binaryAnnotationType
  type endpointType
  type spanType

  def apply(a: annotationType): Annotation
  def apply(a: Annotation): annotationType

  def apply(a: annotationTypeType): AnnotationType
  def apply(a: AnnotationType): annotationTypeType

  def apply(b: binaryAnnotationType): BinaryAnnotation
  def apply(b: BinaryAnnotation): binaryAnnotationType

  def apply(e: endpointType): Endpoint
  def apply(e: Endpoint): endpointType

  def apply(s: spanType): Span
  def apply(s: Span): spanType
}
