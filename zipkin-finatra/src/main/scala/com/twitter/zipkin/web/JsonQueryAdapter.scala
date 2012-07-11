package com.twitter.zipkin.web

import com.twitter.zipkin.adapter.QueryAdapter
import com.twitter.zipkin.query.{TraceCombo, TraceTimeline, TimelineAnnotation}
import com.twitter.zipkin.common.Trace

object JsonQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = TimelineAnnotation
  type traceTimelineType      = JsonTraceTimeline
  type traceComboType         = JsonTraceCombo
  type traceType              = JsonTrace

  /* no change between json and common */
  def apply(t: timelineAnnotationType): TimelineAnnotation = t
  //def apply(t: TimelineAnnotation): timelineAnnotationType

  /* json to common */
  def apply(t: traceTimelineType): TraceTimeline = {

  }

  /* common to json */
  def apply(t: TraceTimeline): traceTimelineType = {

  }

  /* json to common */
  def apply(t: traceComboType): TraceCombo = {

  }

  /* common to json */
  def apply(t: TraceCombo): traceComboType = {

  }

  /* json to common */
  def apply(t: traceType): Trace = {

  }

  /* common to json */
  def apply(t: Trace): traceType = {

  }
}
