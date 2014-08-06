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
package com.twitter.zipkin.conversions

import com.twitter.conversions.time._
import com.twitter.zipkin.common._
import com.twitter.zipkin.gen
import com.twitter.zipkin.query._
import com.twitter.util.{Duration, Time}
import java.util.concurrent.TimeUnit
import com.twitter.algebird.Moments

/**
 * Convenience implicits for converting between common classes and Thrift.
 */
object thrift {
  /* Endpoint */
  class ThriftEndpoint(e: Endpoint) {
    lazy val toThrift = gen.Endpoint(e.ipv4, e.port, e.serviceName)
  }
  class WrappedEndpoint(e: gen.Endpoint) {
    lazy val toEndpoint = {
      val serviceName = e.serviceName match {
        case (null | "") => Endpoint.UnknownServiceName
        case _ => e.serviceName
      }
      new Endpoint(e.ipv4, e.port, serviceName)
    }
  }
  implicit def endpointToThriftEndpoint(e: Endpoint) = new ThriftEndpoint(e)
  implicit def thriftEndpointToEndpoint(e: gen.Endpoint) = new WrappedEndpoint(e)

  /* AnnotationType */
  class ThriftAnnotationType(a: AnnotationType) {
    lazy val toThrift = gen.AnnotationType(a.value)
  }
  class WrappedAnnotationType(a: gen.AnnotationType) {
    lazy val toAnnotationType = AnnotationType(a.value, a.name)
  }
  implicit def annotationTypeToThriftAnnotationType(a: AnnotationType) = new ThriftAnnotationType(a)
  implicit def thriftAnnotationTypeToAnnotationType(a: gen.AnnotationType) = new WrappedAnnotationType(a)

  /* Annotation */
  class ThriftAnnotation(a: Annotation) {
    lazy val toThrift = {
      gen.Annotation(a.timestamp, a.value, a.host.map { _.toThrift }, a.duration.map(_.inMicroseconds.toInt))
    }
  }
  class WrappedAnnotation(a: gen.Annotation) {
    lazy val toAnnotation = {
      if (a.timestamp <= 0)
        throw new IllegalArgumentException("Annotation must have a timestamp: %s".format(a.toString))

      if ("".equals(a.value))
        throw new IllegalArgumentException("Annotation must have a value: %s".format(a.toString))

      new Annotation(a.timestamp, a.value, a.host.map { _.toEndpoint }, a.duration.map { _.microseconds })
    }
  }
  implicit def annotationToThriftAnnotation(a: Annotation) = new ThriftAnnotation(a)
  implicit def thriftAnnotationToAnnotation(a: gen.Annotation) = new WrappedAnnotation(a)

  /* BinaryAnnotation */
  class ThriftBinaryAnnotation(b: BinaryAnnotation) {
    lazy val toThrift = {
      gen.BinaryAnnotation(b.key, b.value, b.annotationType.toThrift, b.host.map { _.toThrift })
    }
  }
  class WrappedBinaryAnnotation(b: gen.BinaryAnnotation) {
    lazy val toBinaryAnnotation = {
      BinaryAnnotation(b.key, b.value, b.annotationType.toAnnotationType, b.host.map { _.toEndpoint })
    }
  }
  implicit def binaryAnnotationToThriftBinaryAnnotation(b: BinaryAnnotation) = new ThriftBinaryAnnotation(b)
  implicit def thriftBinaryAnnotationToBinaryAnnotation(b: gen.BinaryAnnotation) = new WrappedBinaryAnnotation(b)

  /* Span */
  class ThriftSpan(s: Span) {
    lazy val toThrift = {
      gen.Span(s.traceId, s.name, s.id, s.parentId, s.annotations.map { _.toThrift },
        s.binaryAnnotations.map { _.toThrift }, s.debug)
    }
  }
  class WrappedSpan(s: gen.Span) {
    lazy val toSpan = {
      s.name match {
        case null => throw new IncompleteTraceDataException("No name set in Span")
        case _ => ()
      }

      val annotations = s.annotations match {
        case null => List.empty[Annotation]
        case as => as.map { _.toAnnotation }.toList
      }

      val binaryAnnotations = s.binaryAnnotations match {
        case null => List.empty[BinaryAnnotation]
        case b => b.map { _.toBinaryAnnotation }
      }

      new Span(s.traceId, s.name, s.id, s.parentId, annotations, binaryAnnotations, s.debug)
    }
  }
  implicit def spanToThriftSpan(s: Span) = new ThriftSpan(s)
  implicit def thriftSpanToSpan(s: gen.Span) = new WrappedSpan(s)

  /* Order */
  class WrappedOrder(o: Order) {
    lazy val toThrift = {
      o match {
        case Order.DurationDesc  => gen.Order.DurationDesc
        case Order.DurationAsc   => gen.Order.DurationAsc
        case Order.TimestampDesc => gen.Order.TimestampDesc
        case Order.TimestampAsc  => gen.Order.TimestampAsc
        case Order.None          => gen.Order.None
      }
    }
  }
  class ThriftOrder(o: gen.Order) {
    lazy val toOrder = {
      o match {
        case gen.Order.DurationDesc  => Order.DurationDesc
        case gen.Order.DurationAsc   => Order.DurationAsc
        case gen.Order.TimestampDesc => Order.TimestampDesc
        case gen.Order.TimestampAsc  => Order.TimestampAsc
        case gen.Order.None          => Order.None
      }
    }
  }
  implicit def orderToThrift(o: Order) = new WrappedOrder(o)
  implicit def thriftToOrder(o: gen.Order) = new ThriftOrder(o)

  /* TimelineAnnotation */
  class WrappedTimelineAnnotation(t: TimelineAnnotation) {
    lazy val toThrift = {
      gen.TimelineAnnotation(
        t.timestamp,
        t.value,
        t.host.toThrift,
        t.spanId,
        t.parentId,
        t.serviceName,
        t.spanName)
    }
  }
  class ThriftTimelineAnnotation(t: gen.TimelineAnnotation) {
    lazy val toTimelineAnnotation = {
      TimelineAnnotation(
        t.timestamp,
        t.value,
        t.host.toEndpoint,
        t.spanId,
        t.parentId,
        t.serviceName,
        t.spanName)
    }
  }
  implicit def timelineAnnotationToThrift(t: TimelineAnnotation) = new WrappedTimelineAnnotation(t)
  implicit def thriftToTimelineAnnotation(t: gen.TimelineAnnotation) = new ThriftTimelineAnnotation(t)

  /* Trace */
  class WrappedTrace(t: Trace) {
    lazy val toThrift = gen.Trace(t.spans.map{ _.toThrift })
  }
  class ThriftTrace(t: gen.Trace) {
    lazy val toTrace = Trace(t.spans.map { _.toSpan })
  }
  implicit def traceToThrift(t: Trace) = new WrappedTrace(t)
  implicit def thriftToTrace(t: gen.Trace) = new ThriftTrace(t)

  /* TraceTimeline */
  class WrappedTraceTimeline(t: TraceTimeline) {
    lazy val toThrift = {
      gen.TraceTimeline(
        t.traceId,
        t.rootSpanId,
        t.annotations.map { _.toThrift },
        t.binaryAnnotations.map { _.toThrift })
    }
  }
  class ThriftTraceTimeline(t: gen.TraceTimeline) {
    lazy val toTraceTimeline = {
      TraceTimeline(
        t.traceId,
        t.rootMostSpanId,
        t.annotations.map { _.toTimelineAnnotation },
        t.binaryAnnotations.map { _.toBinaryAnnotation })
    }
  }
  implicit def traceTimelineToThrift(t: TraceTimeline) = new WrappedTraceTimeline(t)
  implicit def thriftToTraceTimeline(t: gen.TraceTimeline) = new ThriftTraceTimeline(t)

  class WrappedSpanTimestamp(t: SpanTimestamp) {
    lazy val toThrift = gen.SpanTimestamp(t.name, t.startTimestamp, t.endTimestamp)
  }
  class ThriftSpanTimestamp(t: gen.SpanTimestamp) {
    lazy val toSpanTimestamp = SpanTimestamp(t.name, t.startTimestamp, t.endTimestamp)
  }
  implicit def spanTimestampToThrift(t: SpanTimestamp) = new WrappedSpanTimestamp(t)
  implicit def thriftToSpanTimestamp(t: gen.SpanTimestamp) = new ThriftSpanTimestamp(t)

  /* TraceSummary */
  class WrappedTraceSummary(t: TraceSummary) {
    lazy val toThrift = gen.TraceSummary(
      t.traceId,
      t.startTimestamp,
      t.endTimestamp,
      t.durationMicro,
      t.endpoints.map(_.toThrift),
      t.spanTimestamps.map(_.toThrift))
  }
  class ThriftTraceSummary(t: gen.TraceSummary) {
    lazy val toTraceSummary = TraceSummary(
      t.traceId,
      t.startTimestamp,
      t.endTimestamp,
      t.durationMicro,
      t.spanTimestamps.map(_.toSpanTimestamp).toList,
      t.endpoints.map(_.toEndpoint).toList)
  }
  implicit def traceSummaryToThrift(t: TraceSummary) = new WrappedTraceSummary(t)
  implicit def thriftToTraceSummary(t: gen.TraceSummary) = new ThriftTraceSummary(t)

  /* TraceCombo */
  class WrappedTraceCombo(t: TraceCombo) {
    lazy val toThrift = {
      gen.TraceCombo(
        t.trace.toThrift,
        t.traceSummary map { _.toThrift },
        t.traceTimeline map { _.toThrift },
        t.spanDepths)
    }
  }
  class ThriftTraceCombo(t: gen.TraceCombo) {
    lazy val toTraceCombo = {
      TraceCombo(
        t.trace.toTrace,
        t.summary map { _.toTraceSummary },
        t.timeline map { _.toTraceTimeline },
        t.spanDepths map {_.toMap })
    }
  }
  implicit def traceComboToThrift(t: TraceCombo) = new WrappedTraceCombo(t)
  implicit def thriftToTraceCombo(t: gen.TraceCombo) = new ThriftTraceCombo(t)

  /* QueryRequest */
  class WrappedQueryRequest(q: QueryRequest) {
    lazy val toThrift = {
      gen.QueryRequest(
        q.serviceName,
        q.spanName,
        q.annotations,
        q.binaryAnnotations.map {
          _.map { _.toThrift }
        },
        q.endTs,
        q.limit,
        q.order.toThrift)
    }
  }
  class ThriftQueryRequest(q: gen.QueryRequest) {
    lazy val toQueryRequest = {
      QueryRequest(
        q.serviceName,
        q.spanName,
        q.annotations,
        q.binaryAnnotations map {
          _ map { _.toBinaryAnnotation }
        },
        q.endTs,
        q.limit,
        q.order.toOrder)
    }
  }
  implicit def queryRequestToThrift(q: QueryRequest) = new WrappedQueryRequest(q)
  implicit def thriftToQueryRequest(q: gen.QueryRequest) = new ThriftQueryRequest(q)

  /* QueryResponse */
  class WrappedQueryResponse(q: QueryResponse) {
    lazy val toThrift = gen.QueryResponse(q.traceIds, q.startTs, q.endTs)
  }
  class ThriftQueryResponse(q: gen.QueryResponse) {
    lazy val toQueryResponse = QueryResponse(q.traceIds, q.startTs, q.endTs)
  }
  implicit def queryResponseToThrift(q: QueryResponse) = new WrappedQueryResponse(q)
  implicit def thriftToQueryResponse(q: gen.QueryResponse) = new ThriftQueryResponse(q)

  /* Dependencies */
  class WrappedMoments(m: Moments) {
    lazy val toThrift = gen.Moments(m.m0, m.m1, m.m2, m.m3, m.m4)
  }
  class ThriftMoments(m: gen.Moments) {
    lazy val toMoments = Moments(m.m0, m.m1, m.m2, m.m3, m.m4)
  }
  implicit def momentsToThrift(m: Moments) = new WrappedMoments(m)
  implicit def thriftToMoments(m: gen.Moments) = new ThriftMoments(m)

  class WrappedDependencyLink(dl: DependencyLink) {
    lazy val toThrift = {
      gen.DependencyLink(dl.parent.name, dl.child.name, dl.durationMoments.toThrift)
    }
  }
  class ThriftDependencyLink(dl: gen.DependencyLink) {
    lazy val toDependencyLink = DependencyLink(
      Service(dl.parent),
      Service(dl.child),
      dl.durationMoments.toMoments
    )
  }
  implicit def dependencyLinkToThrift(dl: DependencyLink) = new WrappedDependencyLink(dl)
  implicit def thriftToDependencyLink(dl: gen.DependencyLink) = new ThriftDependencyLink(dl)
  class WrappedDependencies(d: Dependencies) {
    lazy val toThrift = gen.Dependencies(d.startTime.inMicroseconds, d.endTime.inMicroseconds, d.links.map {_.toThrift}.toSeq )
  }
  class ThriftDependencies(d: gen.Dependencies) {
    lazy val toDependencies = Dependencies(
      Time.fromMicroseconds(d.startTime),
      Time.fromMicroseconds(d.endTime),
      d.links.map {_.toDependencyLink}
    )
  }
  implicit def dependenciesToThrift(d: Dependencies) = new WrappedDependencies(d)
  implicit def thriftToDependencies(d: gen.Dependencies) = new ThriftDependencies(d)
}
