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

import com.twitter.zipkin.query._

/**
 * Adapter for query related structs
 */
trait QueryAdapter {
  type timelineAnnotationType /* corresponds to com.twitter.zipkin.query.TimelineAnnotation */
  type traceTimelineType      /* corresponds to com.twitter.zipkin.query.TraceTimeline */
  type traceComboType         /* corresponds to com.twitter.zipkin.query.TraceCombo */
  type traceSummaryType      /* corresponds to com.twitter.zipkin.common.TraceSummary     */
  type traceType              /* corresponds to com.twitter.zipkin.query.Trace */

  type queryAnnotationType /* corresponds to com.twitter.zipkin.query.QueryAnnotation */
  type queryRequestType    /* corresponds to com.twitter.zipkin.query.QueryRequest    */
  type queryResponseType   /* corresponds to com.twitter.zipkin.query.QueryResponse   */

  type orderType           /* corresponds to com.twitter.zipkin.query.Order */

  def apply(t: timelineAnnotationType): TimelineAnnotation
  def apply(t: TimelineAnnotation): timelineAnnotationType

  def apply(t: traceTimelineType): TraceTimeline
  def apply(t: TraceTimeline): traceTimelineType

  def apply(t: traceComboType): TraceCombo
  def apply(t: TraceCombo): traceComboType

  def apply(t: traceSummaryType): TraceSummary
  def apply(t: TraceSummary): traceSummaryType

  def apply(t: traceType): Trace
  def apply(t: Trace): traceType

  def apply(q: queryAnnotationType): QueryAnnotation
  def apply(q: QueryAnnotation): queryAnnotationType

  def apply(q: queryRequestType): QueryRequest
  def apply(q: QueryRequest): queryRequestType

  def apply(q: queryResponseType): QueryResponse
  def apply(q: QueryResponse): queryResponseType

  def apply(o: orderType): Order
  def apply(o: Order): orderType
}
