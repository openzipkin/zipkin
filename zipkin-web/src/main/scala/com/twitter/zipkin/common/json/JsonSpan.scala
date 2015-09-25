/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Span
import com.twitter.finagle.tracing.SpanId

case class JsonSpan(
  traceId: String,
  name: String,
  id: String,
  parentId: Option[String],
  services: Set[String],
  startTimestamp: Option[Long],
  duration: Option[Long],
  annotations: List[JsonAnnotation],
  binaryAnnotations: Seq[JsonBinaryAnnotation]) extends WrappedJson

object JsonSpan {
  def wrap(s: Span) = {
    new JsonSpan(
      SpanId(s.traceId).toString, // not a bug, SpanId converts Long to hex string
      s.name,
      SpanId(s.id).toString,
      s.parentId.map(SpanId(_).toString),
      s.serviceNames,
      s.firstAnnotation.map {_.timestamp},
      s.duration,
      s.annotations.map { JsonAnnotation.wrap(_) },
      s.binaryAnnotations.map { JsonBinaryAnnotation.wrap(_) })
  }
}
