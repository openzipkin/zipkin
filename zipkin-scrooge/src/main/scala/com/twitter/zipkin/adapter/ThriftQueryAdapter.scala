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
import com.twitter.zipkin.query._

object ThriftQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = gen.TimelineAnnotation
  type traceTimelineType = gen.TraceTimeline
  type traceComboType = gen.TraceCombo
  type traceSummaryType = gen.TraceSummary
  type traceType = gen.Trace

  type queryRequestType = gen.QueryRequest
  type queryResponseType = gen.QueryResponse

  type orderType = gen.Order

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
      t.`summary`.map(ThriftQueryAdapter(_)),
      t.`timeline`.map(ThriftQueryAdapter(_)),
      t.`spanDepths`.map(_.toMap))
  }

  /* TraceCombo to Thrift */
  def apply(t: TraceCombo): traceComboType = {
    gen.TraceCombo(
      ThriftQueryAdapter(t.trace),
      t.traceSummary.map(ThriftQueryAdapter(_)),
      t.traceTimeline.map(ThriftQueryAdapter(_)),
      t.spanDepths)
  }

  /* TraceSummary from Thrift */
  def apply(t: traceSummaryType): TraceSummary = {
    new TraceSummary(t.traceId, t.startTimestamp, t.endTimestamp,
      t.durationMicro, t.serviceCounts,
      t.endpoints.map(ThriftAdapter(_)).toList)
  }

  /* TraceSummary to Thrift */
  def apply(t: TraceSummary): traceSummaryType = {
    gen.TraceSummary(t.traceId, t.startTimestamp, t.endTimestamp,
      t.durationMicro, t.serviceCounts, t.endpoints.map(ThriftAdapter(_)))
  }

  /* Trace from Thrift */
  def apply(t: traceType): Trace = {
    Trace(t.`spans`.map(ThriftAdapter(_)))
  }

  /* Trace to Thrift */
  def apply(t: Trace): traceType = {
    gen.Trace(t.spans.map(ThriftAdapter(_)))
  }

  /* QueryRequest */
  def apply(q: queryRequestType): QueryRequest = {
    QueryRequest(
      q.`serviceName`,
      q.`spanName`,
      q.`annotations`,
      q.`binaryAnnotations`.map {
        _.map { ThriftAdapter(_) }
      },
      q.`endTs`,
      q.`limit`,
      ThriftQueryAdapter(q.`order`))
  }
  def apply(q: QueryRequest): queryRequestType = {
    gen.QueryRequest(
      q.serviceName,
      q.spanName,
      q.annotations,
      q.binaryAnnotations.map {
        _.map { ThriftAdapter(_) }
      },
      q.endTs,
      q.limit,
      ThriftQueryAdapter(q.order))
  }

  /* QueryResponse */
  def apply(q: queryResponseType): QueryResponse =
    QueryResponse(q.`traceIds`, q.`annotationsCounts`, q.`binaryAnnotationsCounts`, q.`startTs`, q.`endTs`)
  def apply(q: QueryResponse): queryResponseType =
    gen.QueryResponse(q.traceIds, q.annotationsCounts, q.binaryAnnotationsCounts, q.startTs, q.endTs)

  /* Order */
  def apply(o: orderType): Order = {
    o match {
      case gen.Order.DurationDesc  => Order.DurationDesc
      case gen.Order.DurationAsc   => Order.DurationAsc
      case gen.Order.TimestampDesc => Order.TimestampDesc
      case gen.Order.TimestampAsc  => Order.TimestampAsc
      case gen.Order.None          => Order.None
    }
  }
  def apply(o: Order): orderType = {
    o match {
      case Order.DurationDesc  => gen.Order.DurationDesc
      case Order.DurationAsc   => gen.Order.DurationAsc
      case Order.TimestampDesc => gen.Order.TimestampDesc
      case Order.TimestampAsc  => gen.Order.TimestampAsc
      case Order.None          => gen.Order.None
    }
  }
}
