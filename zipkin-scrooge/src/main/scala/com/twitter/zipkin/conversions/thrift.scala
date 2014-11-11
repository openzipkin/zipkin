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

import com.twitter.algebird.Moments
import com.twitter.conversions.time._
import com.twitter.util.{Duration, Time}
import com.twitter.zipkin.common._
import com.twitter.zipkin.query._
import com.twitter.zipkin.thriftscala
import java.util.concurrent.TimeUnit
import scala.language.implicitConversions

/**
 * Convenience implicits for converting between common classes and Thrift.
 */
object thrift {
  /* Endpoint */
  class ThriftEndpoint(e: Endpoint) {
    lazy val toThrift = thriftscala.Endpoint(e.ipv4, e.port, e.serviceName)
  }
  class WrappedEndpoint(e: thriftscala.Endpoint) {
    lazy val toEndpoint = {
      val serviceName = e.serviceName match {
        case (null | "") => Endpoint.UnknownServiceName
        case _ => e.serviceName
      }
      new Endpoint(e.ipv4, e.port, serviceName)
    }
  }
  implicit def endpointToThriftEndpoint(e: Endpoint) = new ThriftEndpoint(e)
  implicit def thriftEndpointToEndpoint(e: thriftscala.Endpoint) = new WrappedEndpoint(e)

  /* AnnotationType */
  class ThriftAnnotationType(a: AnnotationType) {
    lazy val toThrift = thriftscala.AnnotationType(a.value)
  }
  class WrappedAnnotationType(a: thriftscala.AnnotationType) {
    lazy val toAnnotationType = AnnotationType(a.value, a.name)
  }
  implicit def annotationTypeToThriftAnnotationType(a: AnnotationType) = new ThriftAnnotationType(a)
  implicit def thriftAnnotationTypeToAnnotationType(a: thriftscala.AnnotationType) = new WrappedAnnotationType(a)

  /* Annotation */
  class ThriftAnnotation(a: Annotation) {
    lazy val toThrift = {
      thriftscala.Annotation(a.timestamp, a.value, a.host.map { _.toThrift }, a.duration.map(_.inMicroseconds.toInt))
    }
  }
  class WrappedAnnotation(a: thriftscala.Annotation) {
    lazy val toAnnotation = {
      if (a.timestamp <= 0)
        throw new IllegalArgumentException("Annotation must have a timestamp: %s".format(a.toString))

      if ("".equals(a.value))
        throw new IllegalArgumentException("Annotation must have a value: %s".format(a.toString))

      new Annotation(a.timestamp, a.value, a.host.map { _.toEndpoint }, a.duration.map { _.microseconds })
    }
  }
  implicit def annotationToThriftAnnotation(a: Annotation) = new ThriftAnnotation(a)
  implicit def thriftAnnotationToAnnotation(a: thriftscala.Annotation) = new WrappedAnnotation(a)

  /* BinaryAnnotation */
  class ThriftBinaryAnnotation(b: BinaryAnnotation) {
    lazy val toThrift = {
      thriftscala.BinaryAnnotation(b.key, b.value, b.annotationType.toThrift, b.host.map { _.toThrift })
    }
  }
  class WrappedBinaryAnnotation(b: thriftscala.BinaryAnnotation) {
    lazy val toBinaryAnnotation = {
      BinaryAnnotation(b.key, b.value, b.annotationType.toAnnotationType, b.host.map { _.toEndpoint })
    }
  }
  implicit def binaryAnnotationToThriftBinaryAnnotation(b: BinaryAnnotation) = new ThriftBinaryAnnotation(b)
  implicit def thriftBinaryAnnotationToBinaryAnnotation(b: thriftscala.BinaryAnnotation) = new WrappedBinaryAnnotation(b)

  /* Span */
  class ThriftSpan(s: Span) {
    lazy val toThrift = {
      thriftscala.Span(s.traceId, s.name, s.id, s.parentId, s.annotations.map { _.toThrift },
        s.binaryAnnotations.map { _.toThrift }, s.debug)
    }
  }
  class WrappedSpan(s: thriftscala.Span) {
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
  implicit def thriftSpanToSpan(s: thriftscala.Span) = new WrappedSpan(s)

  /* Order */
  class WrappedOrder(o: Order) {
    lazy val toThrift = {
      o match {
        case Order.DurationDesc  => thriftscala.Order.DurationDesc
        case Order.DurationAsc   => thriftscala.Order.DurationAsc
        case Order.TimestampDesc => thriftscala.Order.TimestampDesc
        case Order.TimestampAsc  => thriftscala.Order.TimestampAsc
        case Order.None          => thriftscala.Order.None
      }
    }
  }
  class ThriftOrder(o: thriftscala.Order) {
    lazy val toOrder = {
      o match {
        case thriftscala.Order.DurationDesc  => Order.DurationDesc
        case thriftscala.Order.DurationAsc   => Order.DurationAsc
        case thriftscala.Order.TimestampDesc => Order.TimestampDesc
        case thriftscala.Order.TimestampAsc  => Order.TimestampAsc
        case thriftscala.Order.None          => Order.None
      }
    }
  }
  implicit def orderToThrift(o: Order) = new WrappedOrder(o)
  implicit def thriftToOrder(o: thriftscala.Order) = new ThriftOrder(o)

  /* TimelineAnnotation */
  class WrappedTimelineAnnotation(t: TimelineAnnotation) {
    lazy val toThrift = {
      thriftscala.TimelineAnnotation(
        t.timestamp,
        t.value,
        t.host.toThrift,
        t.spanId,
        t.parentId,
        t.serviceName,
        t.spanName)
    }
  }
  class ThriftTimelineAnnotation(t: thriftscala.TimelineAnnotation) {
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
  implicit def thriftToTimelineAnnotation(t: thriftscala.TimelineAnnotation) = new ThriftTimelineAnnotation(t)

  /* Trace */
  class WrappedTrace(t: Trace) {
    lazy val toThrift = thriftscala.Trace(t.spans.map{ _.toThrift })
  }
  class ThriftTrace(t: thriftscala.Trace) {
    lazy val toTrace = Trace(t.spans.map { _.toSpan })
  }
  implicit def traceToThrift(t: Trace) = new WrappedTrace(t)
  implicit def thriftToTrace(t: thriftscala.Trace) = new ThriftTrace(t)

  /* TraceTimeline */
  class WrappedTraceTimeline(t: TraceTimeline) {
    lazy val toThrift = {
      thriftscala.TraceTimeline(
        t.traceId,
        t.rootSpanId,
        t.annotations.map { _.toThrift },
        t.binaryAnnotations.map { _.toThrift })
    }
  }
  class ThriftTraceTimeline(t: thriftscala.TraceTimeline) {
    lazy val toTraceTimeline = {
      TraceTimeline(
        t.traceId,
        t.rootMostSpanId,
        t.annotations.map { _.toTimelineAnnotation },
        t.binaryAnnotations.map { _.toBinaryAnnotation })
    }
  }
  implicit def traceTimelineToThrift(t: TraceTimeline) = new WrappedTraceTimeline(t)
  implicit def thriftToTraceTimeline(t: thriftscala.TraceTimeline) = new ThriftTraceTimeline(t)

  class WrappedSpanTimestamp(t: SpanTimestamp) {
    lazy val toThrift = thriftscala.SpanTimestamp(t.name, t.startTimestamp, t.endTimestamp)
  }
  class ThriftSpanTimestamp(t: thriftscala.SpanTimestamp) {
    lazy val toSpanTimestamp = SpanTimestamp(t.name, t.startTimestamp, t.endTimestamp)
  }
  implicit def spanTimestampToThrift(t: SpanTimestamp) = new WrappedSpanTimestamp(t)
  implicit def thriftToSpanTimestamp(t: thriftscala.SpanTimestamp) = new ThriftSpanTimestamp(t)

  /* TraceSummary */
  class WrappedTraceSummary(t: TraceSummary) {
    lazy val toThrift = thriftscala.TraceSummary(
      t.traceId,
      t.startTimestamp,
      t.endTimestamp,
      t.durationMicro,
      t.endpoints.map(_.toThrift),
      t.spanTimestamps.map(_.toThrift))
  }
  class ThriftTraceSummary(t: thriftscala.TraceSummary) {
    lazy val toTraceSummary = TraceSummary(
      t.traceId,
      t.startTimestamp,
      t.endTimestamp,
      t.durationMicro,
      t.spanTimestamps.map(_.toSpanTimestamp).toList,
      t.endpoints.map(_.toEndpoint).toList)
  }
  implicit def traceSummaryToThrift(t: TraceSummary) = new WrappedTraceSummary(t)
  implicit def thriftToTraceSummary(t: thriftscala.TraceSummary) = new ThriftTraceSummary(t)

  /* TraceCombo */
  class WrappedTraceCombo(t: TraceCombo) {
    lazy val toThrift = {
      thriftscala.TraceCombo(
        t.trace.toThrift,
        t.traceSummary map { _.toThrift },
        t.traceTimeline map { _.toThrift },
        t.spanDepths)
    }
  }
  class ThriftTraceCombo(t: thriftscala.TraceCombo) {
    lazy val toTraceCombo = {
      TraceCombo(
        t.trace.toTrace,
        t.summary map { _.toTraceSummary },
        t.timeline map { _.toTraceTimeline },
        t.spanDepths map {_.toMap })
    }
  }
  implicit def traceComboToThrift(t: TraceCombo) = new WrappedTraceCombo(t)
  implicit def thriftToTraceCombo(t: thriftscala.TraceCombo) = new ThriftTraceCombo(t)

  /* QueryRequest */
  class WrappedQueryRequest(q: QueryRequest) {
    lazy val toThrift = {
      thriftscala.QueryRequest(
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
  class ThriftQueryRequest(q: thriftscala.QueryRequest) {
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
  implicit def thriftToQueryRequest(q: thriftscala.QueryRequest) = new ThriftQueryRequest(q)

  /* QueryResponse */
  class WrappedQueryResponse(q: QueryResponse) {
    lazy val toThrift = thriftscala.QueryResponse(q.traceIds, q.startTs, q.endTs)
  }
  class ThriftQueryResponse(q: thriftscala.QueryResponse) {
    lazy val toQueryResponse = QueryResponse(q.traceIds, q.startTs, q.endTs)
  }
  implicit def queryResponseToThrift(q: QueryResponse) = new WrappedQueryResponse(q)
  implicit def thriftToQueryResponse(q: thriftscala.QueryResponse) = new ThriftQueryResponse(q)

  /* Dependencies */
  class WrappedMoments(m: Moments) {
    lazy val toThrift = thriftscala.Moments(m.m0, m.m1, m.m2, m.m3, m.m4)
  }
  class ThriftMoments(m: thriftscala.Moments) {
    lazy val toMoments = Moments(m.m0, m.m1, m.m2, m.m3, m.m4)
  }
  implicit def momentsToThrift(m: Moments) = new WrappedMoments(m)
  implicit def thriftToMoments(m: thriftscala.Moments) = new ThriftMoments(m)

  class WrappedDependencyLink(dl: DependencyLink) {
    lazy val toThrift = {
      thriftscala.DependencyLink(dl.parent.name, dl.child.name, dl.durationMoments.toThrift)
    }
  }
  class ThriftDependencyLink(dl: thriftscala.DependencyLink) {
    lazy val toDependencyLink = DependencyLink(
      Service(dl.parent),
      Service(dl.child),
      dl.durationMoments.toMoments
    )
  }
  implicit def dependencyLinkToThrift(dl: DependencyLink) = new WrappedDependencyLink(dl)
  implicit def thriftToDependencyLink(dl: thriftscala.DependencyLink) = new ThriftDependencyLink(dl)
  class WrappedDependencies(d: Dependencies) {
    lazy val toThrift = thriftscala.Dependencies(d.startTime.inMicroseconds, d.endTime.inMicroseconds, d.links.map {_.toThrift}.toSeq )
  }
  class ThriftDependencies(d: thriftscala.Dependencies) {
    lazy val toDependencies = Dependencies(
      Time.fromMicroseconds(d.startTime),
      Time.fromMicroseconds(d.endTime),
      d.links.map {_.toDependencyLink}
    )
  }
  implicit def dependenciesToThrift(d: Dependencies) = new WrappedDependencies(d)
  implicit def thriftToDependencies(d: thriftscala.Dependencies) = new ThriftDependencies(d)
}
