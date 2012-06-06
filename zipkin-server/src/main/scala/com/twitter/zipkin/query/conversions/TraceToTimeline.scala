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
package com.twitter.zipkin.query.conversions

import com.twitter.zipkin.gen
import com.twitter.zipkin.common.{IncompleteTraceDataException, Endpoint, Trace}

class TraceToTimeline {

  def toTraceTimeline(trace: Trace): Option[gen.TraceTimeline] = {

    if (trace.spans.isEmpty) {
      return None
    }

    // convert span and annotation to timeline annotation
    val annotations = trace.spans.flatMap(s =>
      s.annotations.map{ a =>
        gen.TimelineAnnotation(a.timestamp, a.value,
          a.host match {
            case Some(s) => s.toThrift
            case None => Endpoint.Unknown.toThrift
          },
          s.id,
          s.parentId,
          a.host match {
            case Some(s) => s.serviceName
             case None => "Unknown"
          },
          s.name)
      }
    ).sortWith((a, b) => {
      a.timestamp < b.timestamp

      // TODO also sort so that events that must have happened first (cs before sr for example)
      // end up in the right order
    })

    val rootSpanId = trace.getRootMostSpan.getOrElse(return None).id
    val id = trace.id.getOrElse(return None)

    Some(gen.TraceTimeline(id, rootSpanId, annotations, trace.getBinaryAnnotations))
  }

}