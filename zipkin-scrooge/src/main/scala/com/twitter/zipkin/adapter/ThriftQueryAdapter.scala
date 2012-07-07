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
package com.twitter.zipkin.adapter

import com.twitter.zipkin.gen
import com.twitter.zipkin.query.{TraceCombo, TraceTimeline, TimelineAnnotation}
import com.twitter.zipkin.common.Trace

object ThriftQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = gen.TimelineAnnotation
  type traceTimelineType = gen.TraceTimeline
  type traceComboType = gen.TraceCombo
  type traceType = gen.Trace

  /* TimelineAnnotation from Thrift */
  def apply(t: timelineAnnotationType): TimelineAnnotation = {
    TimelineAnnotation(
      t.`timestamp`,
      t.`value`,
      ThriftAdapter(t.`host`),
      t.`spanId`,
      t.`parentId`,
      t.`serviceName`,
      t.`spanName`)
  }

  /* TimelineAnnotation to Thrift */
  def apply(t: TimelineAnnotation): timelineAnnotationType = {
    gen.TimelineAnnotation(
      t.timestamp,
      t.value,
      ThriftAdapter(t.host),
      t.spanId,
      t.parentId,
      t.serviceName,
      t.spanName)
  }

  /* TraceTimeline from Thrift */
  def apply(t: traceTimelineType): TraceTimeline = {
    TraceTimeline(
      t.`traceId`,
      t.`rootMostSpanId`,
      t.`annotations`.map { ThriftQueryAdapter(_) },
      t.`binaryAnnotations`.map { ThriftAdapter(_) })
  }

  /* TraceTimeline to Thrift */
  def apply(t: TraceTimeline): traceTimelineType = {
    gen.TraceTimeline(
      t.traceId,
      t.rootSpanId,
      t.annotations.map { ThriftQueryAdapter(_) },
      t.binaryAnnotations.map { ThriftAdapter(_) })
  }

  /* TraceCombo from Thrift */
  def apply(t: traceComboType): TraceCombo = {
    TraceCombo(
      ThriftQueryAdapter(t.`trace`),
      t.`summary`.map(ThriftAdapter(_)),
      t.`timeline`.map(ThriftQueryAdapter(_)),
      t.`spanDepths`.map(_.toMap))
  }

  /* TraceCombo to Thrift */
  def apply(t: TraceCombo): traceComboType = {
    gen.TraceCombo(
      ThriftQueryAdapter(t.trace),
      t.traceSummary.map(ThriftAdapter(_)),
      t.traceTimeline.map(ThriftQueryAdapter(_)),
      t.spanDepths)
  }

  /* Trace from Thrift */
  def apply(t: traceType): Trace = {
    Trace(t.`spans`.map(ThriftAdapter(_)))
  }

  /* Trace to Thrift */
  def apply(t: Trace): traceType = {
    gen.Trace(t.spans.map(ThriftAdapter(_)))
  }
}
