/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.query

import java.nio.ByteBuffer

import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.query.adjusters.{NullAdjuster, TimeSkewAdjuster}
import com.twitter.zipkin.storage._
import com.twitter.zipkin.thriftscala
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class QueryServiceSpec extends WordSpec with Matchers with MockitoSugar {
  val ep1 = Endpoint(123, 123, "service1")
  val ep2 = Endpoint(234, 234, "service2")
  val ep3 = Endpoint(345, 345, "service3")
  val ann1 = Annotation(100, thriftscala.Constants.CLIENT_SEND, Some(ep1))
  val ann2 = Annotation(150, thriftscala.Constants.CLIENT_RECV, Some(ep1))
  val spans1 = List(Span(1, "methodcall", 666, None, List(ann1, ann2), Nil))
  val trace1 = Trace(spans1)
  // duration 50

  val ann3 = Annotation(101, thriftscala.Constants.CLIENT_SEND, Some(ep2))
  val ann4 = Annotation(501, thriftscala.Constants.CLIENT_RECV, Some(ep2))
  val spans2 = List(Span(2, "methodcall", 667, None, List(ann3, ann4), Nil))
  val trace2 = Trace(spans2)
  // duration 400

  val ann5 = Annotation(99, thriftscala.Constants.CLIENT_SEND, Some(ep3))
  val ann6 = Annotation(199, thriftscala.Constants.CLIENT_RECV, Some(ep3))
  val spans3 = List(Span(3, "methodcall", 668, None, List(ann5, ann6), Nil))
  val trace3 = Trace(spans3)
  // duration 100

  // get some server action going on
  val ann7 = Annotation(110, thriftscala.Constants.SERVER_RECV, Some(ep2))
  val ann8 = Annotation(140, thriftscala.Constants.SERVER_SEND, Some(ep2))

  val spans4 = List(Span(1, "methodcall", 666, None, List(ann1, ann2), Nil),
    Span(1, "methodcall", 666, None, List(ann7, ann8), Nil))
  val trace4 = Trace(spans4)

  // no spans
  val trace5 = Trace(List())

  "QueryService" should {

    "thriftscala.rate exception in getTraceIdsByName if service name is null" in {
      val qs = new QueryService(null, null, null, null)
      qs.start
      intercept[thriftscala.QueryException] {
        Await.result(qs.getTraceIdsBySpanName(null, "span", 101, 100, thriftscala.Order.DurationDesc))
      }
    }

    "throw exception in getTraceIdsByServiceName if service name is null" in {
      val qs = new QueryService(null, null, null, null)
      qs.start
      intercept[thriftscala.QueryException] {
        Await.result(qs.getTraceIdsByServiceName(null, 101, 100, thriftscala.Order.DurationDesc))
      }
    }

    "throw exception in getTraceIdsByAnnotation if annotation is null" in {
      val qs = new QueryService(null, null, null, null)
      qs.start
      intercept[thriftscala.QueryException] {
        Await.result(qs.getTraceIdsByAnnotation(null, null, null, 101, 100, thriftscala.Order.DurationDesc))
      }
    }

    class MockIndex extends Index {
      def ids = Seq(IndexedTraceId(1, 1), IndexedTraceId(2, 2), IndexedTraceId(3, 3))
      def mockSpanName: Option[String] = Some("methodcall")
      def mockValue: Option[ByteBuffer] = None

      def close() = null
      def getTraceIdsByName(serviceName: String, spanName: Option[String],
                            endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
        serviceName should be ("service")
        spanName should be (mockSpanName)
        endTs should be (100L)
        Future(ids)
      }
      def getTraceIdsByAnnotation(service: String, annotation: String, value: Option[ByteBuffer], endTs: Long,
                                  limit: Int): Future[Seq[IndexedTraceId]] = {
        service should be ("service")
        annotation should be ("annotation")
        value should be (mockValue)
        endTs should be (100L)
        Future(ids)
      }
      def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
        traceIds should be (Seq(1, 2, 3))
        Future(Seq(TraceIdDuration(1, 50, 100), TraceIdDuration(2, 401, 101),
          TraceIdDuration(3, 100, 99)))
      }
      def getServiceNames = null
      def getSpanNames(service: String) = null
      def indexTraceIdByServiceAndName(span: Span) = null
      def indexSpanByAnnotations(span: Span) = null
      def indexServiceName(span: Span) = null
      def indexSpanNameByService(span: Span) = null
      def indexSpanDuration(span: Span): Future[Unit] = null
    }

    "find traces in service span name index, fetch from storage" in {
      val storage = mock[Storage]
      val index = new MockIndex {}

      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsBySpanName("service", "methodcall", 100, 50, thriftscala.Order.DurationDesc))
      result should be (List(2, 3, 1))
    }

    "find traces in service span name index, order by duration desc" in {
      val storage = mock[Storage]
      val index = new MockIndex {}

      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsBySpanName("service", "methodcall", 100, 50, thriftscala.Order.DurationDesc))
      result should be (List(2, 3, 1))
    }

    "find traces in service span name index, order by nothing" in {
      val storage = mock[Storage]
      val index = new MockIndex {}

      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsBySpanName("service", "methodcall", 100, 50, thriftscala.Order.None))
      result should be (List(1, 2, 3))
    }

    "successfully return the trace summary for a trace id" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val traceId = 123L

      when(storage.getSpansByTraceIds(List(traceId))) thenReturn Future(List(spans1))

      val result = List(TraceSummary(1, 100, 150, 50, List(SpanTimestamp("service1", 100, 150)), List(ep1)).toThrift)
      result should be (Await.result(qs.getTraceSummariesByIds(List(traceId), List())))
    }

    "successfully return the trace combo for a trace id" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val traceId = 123L

      when(storage.getSpansByTraceIds(List(traceId))) thenReturn Future(List(spans1))

      val trace = trace1.toThrift
      val summary = TraceSummary(1, 100, 150, 50, List(SpanTimestamp("service1", 100, 150)), List(ep1)).toThrift
      val timeline = TraceTimeline(trace1) map { _.toThrift }

      val result = Await.result(qs.getTraceCombosByIds(List(traceId), List()))
      result should be (Seq(thriftscala.TraceCombo(trace, Some(summary), timeline, Some(Map(666L -> 1)))))
    }

    "find traces in service name index, fetch from storage" in {
      val storage = mock[Storage]
      val index = new MockIndex {
        override def mockSpanName = None
      }
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsByServiceName("service", 100, 50, thriftscala.Order.DurationDesc))
      result should be (List(2, 3, 1))
    }

    "find traces in annotation index by timestamp annotation, fetch from storage" in {
      val storage = mock[Storage]
      val index = new MockIndex {}
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsByAnnotation("service", "annotation", null, 100, 50, thriftscala.Order.DurationDesc))
      result should be (List(2, 3, 1))
    }

    "find traces in annotation index by kv annotation, fetch from storage" in {
      val storage = mock[Storage]
      val index = new MockIndex {
        override def mockValue = Some(ByteBuffer.wrap("value".getBytes))
      }
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsByAnnotation("service", "annotation", ByteBuffer.wrap("value".getBytes), 100, 50, thriftscala.Order.DurationDesc))
      result should be (List(2, 3, 1))
    }

    "fetch traces from storage" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      when(storage.getSpansByTraceIds(List(1L))) thenReturn Future(List(spans1))

      Await.result(qs.getTracesByIds(List(1L), List())) should be (List(trace1.toThrift))
    }

    "fetch timeline from storage" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null,
        Map(thriftscala.Adjust.Nothing -> NullAdjuster, thriftscala.Adjust.TimeSkew -> new TimeSkewAdjuster()))
      qs.start()

      when(storage.getSpansByTraceIds(List(1L))) thenReturn Future(List(spans4))

      val ann1 = thriftscala.TimelineAnnotation(100, thriftscala.Constants.CLIENT_SEND,
        ep1.toThrift, 666, None, "service1", "methodcall")
      val ann2 = thriftscala.TimelineAnnotation(150, thriftscala.Constants.CLIENT_RECV,
        ep1.toThrift, 666, None, "service1", "methodcall")
      val ann3 = thriftscala.TimelineAnnotation(110, thriftscala.Constants.SERVER_RECV,
        ep2.toThrift, 666, None, "service2", "methodcall")
      val ann4 = thriftscala.TimelineAnnotation(140, thriftscala.Constants.SERVER_SEND,
        ep2.toThrift, 666, None, "service2", "methodcall")

      val result = Await.result(qs.getTraceTimelinesByIds(List(1L), List(thriftscala.Adjust.Nothing, thriftscala.Adjust.TimeSkew)))
      result should be (List(thriftscala.TraceTimeline(1L, 666, List(ann1, ann3, ann4, ann2), List())))
    }

    "fetch timeline with clock skew from storage, fix skew" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map(thriftscala.Adjust.TimeSkew -> new TimeSkewAdjuster()))
      qs.start()

      // these are real traces, except for timestap that has been chopped down to make easier to spot
      // differences
      val epKoalabird = Some(Endpoint(170024040, -26945, "koalabird-cuckoo"))
      val epKoalabirdT = epKoalabird.get.toThrift
      val epCuckoo = Some(Endpoint(170061954, 9149, "cuckoo.thrift"))
      val epCuckooT = epCuckoo.get.toThrift
      val epCuckooCassie = Some(Endpoint(170061954, -24936, "client"))
      val epCuckooCassieT = epCuckooCassie.get.toThrift

      val rs1 = Span(4488677265848750007L, "ValuesFromSource", 4488677265848750007L, None, List(
        Annotation(6712580L, "cs", epKoalabird),
        Annotation(6873376L, "cr", epKoalabird),
        Annotation(6711797L, "sr", epCuckoo),
        Annotation(6872073L, "ss", epCuckoo)
        ), Nil)
      val rs2 = Span(4488677265848750007L, "multiget_slice", 2603914853951996887L,
        Some(4488677265848750007L), List(
          Annotation(6712539L, "cs", epCuckooCassie),
          Annotation(6871825L, "cr", epCuckooCassie)
        ), Nil)

      val realSpans = List(rs1, rs2)
      val realTrace = Trace(realSpans)

      when(storage.getSpansByTraceIds(List(4488677265848750007L))) thenReturn Future(List(realSpans))

      val result = Await.result(qs.getTraceTimelinesByIds(List(4488677265848750007L), List(thriftscala.Adjust.TimeSkew)))
      result.size should be (1)
      val tla = result(0).`annotations`
      /*
        we expect the following order of annotations back
        this is the order of the evens as they happened
        Annotation(6712580L, "cs", epKoalabird),
        Annotation(6711797L, "sr", epCuckoo),
        Annotation(6712539L, "cs", epCuckooCassie),
        Annotation(6871825L, "cr", epCuckooCassie)
        Annotation(6872073L, "ss", epCuckoo)
        Annotation(6873376L, "cr", epKoalabird),
      */

      // we ignore the timestamps for now, order is what we care about
      tla(0).`value` should be ("cs")
      tla(0).`host` should be (epKoalabirdT)
      tla(1).`value` should be ("sr")
      tla(1).`host` should be (epCuckooT)
      tla(2).`value` should be ("cs")
      tla(2).`host` should be (epCuckooCassieT)
      tla(3).`value` should be ("cr")
      tla(3).`host` should be (epCuckooCassieT)
      tla(4).`value` should be ("ss")
      tla(4).`host` should be (epCuckooT)
      tla(5).`value` should be ("cr")
      tla(5).`host` should be (epKoalabirdT)
    }

    "fetch timeline with clock skew from storage, fix skew - the sequel" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map(thriftscala.Adjust.TimeSkew -> new TimeSkewAdjuster()))
      qs.start()

      // these are real traces, except for timestap that has been chopped down to make easier to spot
      // differences
      val epKoalabird = Some(Endpoint(170021254, -24672, "koalabird-cuckoo"))
      val epKoalabirdT = epKoalabird.get.toThrift
      val epCuckoo = Some(Endpoint(170062191, 9149, "cuckoo.thrift"))
      val epCuckooT = epCuckoo.get.toThrift
      val epCuckooCassie = Some(Endpoint(170062191, -8985, "client"))
      val epCuckooCassieT = epCuckooCassie.get.toThrift

      val rs1 = Span(-6120267009876080004L, "ValuesFromSource", -6120267009876080004L, None, List(
        Annotation(630715L, "cs", epKoalabird),
        Annotation(832298L, "cr", epKoalabird),
        Annotation(632011L, "sr", epCuckoo),
        Annotation(833041L, "ss", epCuckoo)
        ), Nil)
      val rs2 = Span(-6120267009876080004L, "multiget_slice", 3496046180771443122L,
        Some(-6120267009876080004L), List(
          Annotation(632711L, "cs", epCuckooCassie),
          Annotation(832872L, "cr", epCuckooCassie)
        ), Nil)

      val realSpans = List(rs1, rs2)
      val realTrace = Trace(realSpans)

      when(storage.getSpansByTraceIds(List(-6120267009876080004L))) thenReturn Future(List(realSpans))

      val result = Await.result(qs.getTraceTimelinesByIds(List(-6120267009876080004L), List(thriftscala.Adjust.TimeSkew)))
      result.size should be (1)
      val tla = result(0).`annotations`
      /*
        we expect the following order of annotations back
        this is the order of the evens as they happened

        Annotation(timestamp=630715L, 'koalabird-cuckoo', value='cs'),
        Annotation(timestamp=632011L, 'cuckoo.thrift', value='sr'),
        Annotation(timestamp=632711L, 'client', value='cs'),
        Annotation(timestamp=832872L, 'client', value='cr')
        Annotation(timestamp=833041L, 'cuckoo.thrift', value='ss'),
        Annotation(timestamp=832298L, 'koalabird-cuckoo', value='cr')
        diff between cr - cs: 201583
        diff between ss - sr: 201030
        latency: (201583 - 201030) / 2 = 276
        skew: 1020
        Annotation(timestamp=630715L, 'koalabird-cuckoo', value='cs'),
        Annotation(timestamp=630991, 'cuckoo.thrift', value='sr'),
        Annotation(timestamp=631691, 'client', value='cs'),
        Annotation(timestamp=831852, 'client', value='cr')
        Annotation(timestamp=832021, 'cuckoo.thrift', value='ss'),
        Annotation(timestamp=832298L, 'koalabird-cuckoo', value='cr')
      */

      // we ignore the timestamps for now, order is what we care about
      tla(0).`value` should be ("cs")
      tla(0).`host` should be (epKoalabirdT)
      tla(1).`value` should be ("sr")
      tla(1).`host` should be (epCuckooT)
      tla(2).`value` should be ("cs")
      tla(2).`host` should be (epCuckooCassieT)
      tla(3).`value` should be ("cr")
      tla(3).`host` should be (epCuckooCassieT)
      tla(4).`value` should be ("ss")
      tla(4).`host` should be (epCuckooT)
      tla(5).`value` should be ("cr")
      tla(5).`host` should be (epKoalabirdT)
    }

    "fail to find traces by name in index, return empty" in {
      val storage = mock[Storage]
      val index = new MockIndex {
        override def ids: Seq[IndexedTraceId] = Seq()
        override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = Future(Seq())
      }
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsBySpanName("service", "methodcall", 100, 50, thriftscala.Order.DurationDesc))
      result should be (List())
    }

    "fail to find traces by annotation in index, return empty" in {
      val storage = mock[Storage]
      val index = new MockIndex {
        override def ids: Seq[IndexedTraceId] = Seq()
        override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = Future(Seq())
      }
      val qs = new QueryService(storage, index, null, Map())
      qs.start()

      val result = Await.result(qs.getTraceIdsByAnnotation("service", "annotation", null, 100, 50, thriftscala.Order.DurationDesc))
      result should be (List())
    }

    "return the correct ttl" in {
      val storage = mock[Storage]
      val index = mock[Index]
      val qs = new QueryService(storage, index, null, Map())
      qs.start()
      val ttl = 123

      when(storage.getDataTimeToLive) thenReturn ttl

      Await.result(qs.getDataTimeToLive()) should be (ttl)
    }

    "retrieve aggregates" should {
      val aggregates = mock[Aggregates]
      val serviceName = "mockingbird"
      val annotations = Seq("a", "b", "c")

      "retrieve top annotations" in {
        val qs = new QueryService(null, null, aggregates, Map())
        qs.start()

        when(aggregates.getTopAnnotations(serviceName)) thenReturn Future.value(annotations)

        Await.result(qs.getTopAnnotations(serviceName)) should be (annotations)
      }

      "retrieve top key value annotations" in {
        val qs = new QueryService(null, null, aggregates, Map())
        qs.start()

        when(aggregates.getTopKeyValueAnnotations(serviceName)) thenReturn Future.value(annotations)

        Await.result(qs.getTopKeyValueAnnotations(serviceName)) should be (annotations)
      }
    }

    "getTraceIds" should {
      val mockIndex = mock[Index]
      val qs = new QueryService(null, mockIndex, null, null)
      qs.start()

      val serviceName = "service"
      val spanName = Some("span")
      val annotations = Some(Seq("ann1"))
      val binaryAnnotations = Some(Seq(thriftscala.BinaryAnnotation("key", ByteBuffer.wrap("value".getBytes), thriftscala.AnnotationType.String)))
      val endTs = 100
      val limit = 10
      val order = thriftscala.Order.DurationDesc

      val paddedTs = qs.padTimestamp(endTs)

      def id(id: Long, time: Long) = IndexedTraceId(id, time)

      "get intersection of different filters" in {
        val request = thriftscala.QueryRequest(serviceName, spanName, annotations, binaryAnnotations, endTs, limit, order)

        when(mockIndex.getTraceIdsByName(serviceName, spanName, endTs, 1)) thenReturn Future(Seq(id(1, endTs)))
        when(mockIndex.getTraceIdsByAnnotation(serviceName, "ann1", None, endTs, 1)) thenReturn Future(Seq(id(1, endTs)))
        when(mockIndex.getTraceIdsByAnnotation(serviceName, "key", Some(ByteBuffer.wrap("value".getBytes)), endTs, 1)) thenReturn Future(Seq(id(1, endTs)))

        when(mockIndex.getTraceIdsByName(serviceName, spanName, paddedTs, limit)) thenReturn Future(Seq(id(1, 1), id(2, 2), id(3, 3)))
        when(mockIndex.getTraceIdsByAnnotation(serviceName, "ann1", None, paddedTs, limit)) thenReturn Future(Seq(id(4, 4), id(1, 5), id(3, 4)))
        when(mockIndex.getTraceIdsByAnnotation(serviceName, "key", Some(ByteBuffer.wrap("value".getBytes)), paddedTs, limit)) thenReturn Future(Seq(id(2, 3), id(4, 9), id(1, 9)))

        when(mockIndex.getTracesDuration(Seq(1))) thenReturn Future(Seq(TraceIdDuration(1, 100, 1)))

        val response = Await.result(qs.getTraceIds(request))
        response.`traceIds`.length should be (1)
        response.`traceIds`(0) should be (1)

        response.`endTs` should be (9)
        response.`startTs` should be (9)
      }

      "find intersection" should {
        "one id" in {
          val ids = Seq(
            Seq(IndexedTraceId(1, 100), IndexedTraceId(2, 140)),
            Seq(IndexedTraceId(1, 100))
          )
          qs.traceIdsIntersect(ids) should be (Seq(IndexedTraceId(1, 100)))
        }

        "multiple ids" in {
          val ids = Seq(
            Seq(IndexedTraceId(1, 100), IndexedTraceId(2, 200), IndexedTraceId(3, 300)),
            Seq(IndexedTraceId(2, 200), IndexedTraceId(4, 200), IndexedTraceId(7, 300), IndexedTraceId(3, 300))
          )
          qs.traceIdsIntersect(ids) should be (Seq(IndexedTraceId(2, 200), IndexedTraceId(3, 300)))
        }

        "take max time for each id" in {
          val ids = Seq(
            Seq(IndexedTraceId(1, 100), IndexedTraceId(2, 200), IndexedTraceId(3, 300)),
            Seq(IndexedTraceId(1, 101), IndexedTraceId(2, 202))
          )
          qs.traceIdsIntersect(ids) should be (Seq(IndexedTraceId(2, 202), IndexedTraceId(1, 101)))
        }
      }
    }
  }
}
