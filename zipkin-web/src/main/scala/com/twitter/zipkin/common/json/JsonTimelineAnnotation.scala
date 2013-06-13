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

import com.twitter.zipkin.common.Endpoint
import com.twitter.zipkin.query.TimelineAnnotation
import com.twitter.finagle.tracing.SpanId

case class JsonTimelineAnnotation(timestamp: String, value: String, host: Endpoint, spanId: String, parentId: Option[String],
                                  serviceName: String, spanName: String)
  extends WrappedJson

object JsonTimelineAnnotation {
  def wrap(t: TimelineAnnotation) = {
    JsonTimelineAnnotation(t.timestamp.toString, t.value, t.host, SpanId(t.spanId).toString, t.parentId map { SpanId(_).toString }, t.serviceName, t.spanName)
  }
}