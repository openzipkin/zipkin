package com.twitter.zipkin.adapter

import com.twitter.zipkin.query.{TraceTimeline, TimelineAnnotation}


trait QueryAdapter {
  type timelineAnnotationType /* corresponds to com.twitter.zipkin.query.TimelineAnnotation */
  type traceTimelineType      /* corresponds to com.twitter.zipkin.query.TraceTimeline */

  def apply(t: timelineAnnotationType): TimelineAnnotation
  def apply(t: TimelineAnnotation): timelineAnnotationType

  def apply(t: traceTimelineType): TraceTimeline
  def apply(t: TraceTimeline): traceTimelineType
}
