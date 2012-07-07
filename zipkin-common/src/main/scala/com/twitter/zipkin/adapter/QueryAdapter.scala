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

import com.twitter.zipkin.query.{Trace, TraceCombo, TraceTimeline, TimelineAnnotation}


trait QueryAdapter {
  type timelineAnnotationType /* corresponds to com.twitter.zipkin.query.TimelineAnnotation */
  type traceTimelineType      /* corresponds to com.twitter.zipkin.query.TraceTimeline */
  type traceComboType         /* corresponds to com.twitter.zipkin.query.TraceCombo */
  type traceType              /* corresponds to com.twitter.zipkin.query.Trace */

  def apply(t: timelineAnnotationType): TimelineAnnotation
  def apply(t: TimelineAnnotation): timelineAnnotationType

  def apply(t: traceTimelineType): TraceTimeline
  def apply(t: TraceTimeline): traceTimelineType

  def apply(t: traceComboType): TraceCombo
  def apply(t: TraceCombo): traceComboType

  def apply(t: traceType): Trace
  def apply(t: Trace): traceType
}
