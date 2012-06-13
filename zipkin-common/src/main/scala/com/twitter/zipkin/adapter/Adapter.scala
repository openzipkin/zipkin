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

import com.twitter.zipkin.common._

/**
 * `Adapter` converts the core Zipkin structs from the format
 * the collector initially receives them (ex: Scrooge-generated Thrift)
 * into the common classes.
 *
 * The abstract types should be implemented when extending `Adapter`
 * to be the classes that correspond the common classes
 */
trait Adapter {
  type annotationType        /* corresponds to com.twitter.zipkin.common.Annotation       */
  type annotationTypeType    /* corresponds to com.twitter.zipkin.common.AnnotationType   */
  type binaryAnnotationType  /* corresponds to com.twitter.zipkin.common.BinaryAnnotation */
  type endpointType          /* corresponds to com.twitter.zipkin.common.Endpoint         */
  type spanType              /* corresponds to com.twitter.zipkin.common.Span             */

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
