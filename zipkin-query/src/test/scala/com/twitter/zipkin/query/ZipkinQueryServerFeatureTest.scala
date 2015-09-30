package com.twitter.zipkin.query

import com.twitter.finagle.httpx.Status._
import com.twitter.finatra.http.test.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import com.twitter.util.{Future, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import com.twitter.zipkin.storage.{DependencyStore, InMemorySpanStore, SpanStore}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import java.nio.ByteBuffer

class ZipkinQueryServerFeatureTest extends FeatureTest with MockitoSugar with BeforeAndAfter {
  val spanStore = new InMemorySpanStore()
  val dependencyStore = mock[DependencyStore]
  after {
    spanStore.spans.clear()
  }

  override val server = new EmbeddedHttpServer(new ZipkinQueryServer(spanStore, dependencyStore))

  val ep1 = Endpoint(123, 123, "service1")
  val ep2 = Endpoint(234, 234, "service2")
  val ep3 = Endpoint(345, 345, "service3")
  val ann1 = Annotation(100, Constants.ClientSend, Some(ep1))
  val ann2 = Annotation(150, Constants.ClientRecv, Some(ep1))
  val spans1 = List(Span(1, "methodcall", 666, Some(2), List(ann1, ann2), Nil))
  val trace1 = Trace(spans1)
  // duration 50

  val ann3 = Annotation(101, Constants.ClientSend, Some(ep2))
  val ann4 = Annotation(501, Constants.ClientRecv, Some(ep2))
  val spans2 = List(Span(2, "methodcall", 667, None, List(ann3, ann4), Nil))
  val trace2 = Trace(spans2)
  // duration 400

  val ann5 = Annotation(99, Constants.ClientSend, Some(ep3))
  val ann6 = Annotation(199, Constants.ClientRecv, Some(ep3))
  val spans3 = List(Span(3, "methodcall", 668, None, List(ann5, ann6), Nil))
  val trace3 = Trace(spans3)
  // duration 100

  // get some server action going on
  val ann7 = Annotation(110, Constants.ServerRecv, Some(ep2))
  val ann8 = Annotation(140, Constants.ServerSend, Some(ep2))
  val spans4 = List(
    Span(2, "methodcall", 666, Some(2), List(ann1, ann2), Nil),
    Span(2, "methodcall", 666, Some(2), List(ann7, ann8), Nil))
  val trace4 = Trace(spans4)

  val ann9 = Annotation(60, Constants.ClientSend, Some(ep3))
  val ann10 = Annotation(65, "annotation", Some(ep3))
  val ann11 = Annotation(100, Constants.ClientRecv, Some(ep3))
  val bAnn1 = BinaryAnnotation("annotation", ByteBuffer.wrap("ann".getBytes), AnnotationType.String, Some(ep3))
  val bAnn2 = BinaryAnnotation("binary", ByteBuffer.wrap("ann".getBytes), AnnotationType.Bytes, Some(ep3))
  val spans5 = List(Span(5, "otherMethod", 666, Some(2), List(ann9, ann10, ann11), List(bAnn1, bAnn2)))
  // duration 40

  val allSpans = spans1 ++ spans2 ++ spans3 ++ spans4 ++ spans5

  // no spans
  val emptyTrace = Trace(List())
  val deps = Dependencies(0, Time.now.inMicroseconds, List(DependencyLink("tfe", "mobileweb", 1), DependencyLink("Gizmoduck", "tflock", 2)))

  "get service names" in {
    app.injector.instance[SpanStore].apply(allSpans)

    server.httpGet(
      path = "/api/v1/services",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  "service1",
          |  "service2",
          |  "service3"
          |]
        """.stripMargin)
  }

  "get span names" in {
    app.injector.instance[SpanStore].apply(allSpans)

    server.httpGet(
      path = "/api/v1/spans?serviceName=service1",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  "methodcall"
          |]
        """.stripMargin)
  }

  "get span names when not found" in {
    server.httpGet(
      path = "/api/v1/spans?serviceName=service1",
      andExpect = Ok,
      withJsonBody = "[]")
  }

  "get trace by hex id" in {
    app.injector.instance[SpanStore].apply(spans1)

    server.httpGet(
      path = "/api/v1/trace/0000000000000001",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  {
          |    "traceId" : "0000000000000001",
          |    "name" : "methodcall",
          |    "id" : "000000000000029a",
          |    "parentId" : "0000000000000002",
          |    "annotations" : [
          |      {
          |        "timestamp" : 100,
          |        "value" : "cs",
          |        "endpoint" : {
          |          "serviceName" : "service1",
          |          "ipv4" : "0.0.0.123",
          |          "port" : 123
          |        }
          |      },
          |      {
          |        "timestamp" : 150,
          |        "value" : "cr",
          |        "endpoint" : {
          |          "serviceName" : "service1",
          |          "ipv4" : "0.0.0.123",
          |          "port" : 123
          |        }
          |      }
          |    ],
          |    "binaryAnnotations" : [ ]
          |  }
          |]
        """.stripMargin)
  }

  "get trace by id, not found" in {
    server.httpGet(
      path = "/api/v1/trace/0000000000000001",
      andExpect = NotFound)
  }

  "find traces missing service" in {
    server.httpGet(
      path = "/api/v1/traces?serviceName=",
      andExpect = BadRequest)
  }

  "find traces by service" in {
    app.injector.instance[SpanStore].apply(allSpans)

    // Notice annotations are in order by timestamp
    server.httpGet(
      path = "/api/v1/traces?serviceName=service3",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  [
          |    {
          |      "traceId" : "0000000000000005",
          |      "name" : "otherMethod",
          |      "id" : "000000000000029a",
          |      "parentId" : "0000000000000002",
          |      "annotations" : [
          |        {
          |          "timestamp" : 60,
          |          "value" : "cs",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 65,
          |          "value" : "annotation",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 100,
          |          "value" : "cr",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ],
          |      "binaryAnnotations" : [
          |        {
          |          "key" : "annotation",
          |          "value" : "ann",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "key" : "binary",
          |          "value" : "YW5u",
          |          "type" : "BYTES",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  [
          |    {
          |      "traceId" : "0000000000000003",
          |      "name" : "methodcall",
          |      "id" : "000000000000029c",
          |      "annotations" : [
          |        {
          |          "timestamp" : 99,
          |          "value" : "cs",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 199,
          |          "value" : "cr",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ],
          |      "binaryAnnotations" : [ ]
          |    }
          |  ]
          |]
        """.stripMargin)
  }

  "find traces by span name" in {
    app.injector.instance[SpanStore].apply(allSpans)

    server.httpGet(
      path = "/api/v1/traces?serviceName=service3&spanName=methodcall",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  [
          |    {
          |      "traceId" : "0000000000000003",
          |      "name" : "methodcall",
          |      "id" : "000000000000029c",
          |      "annotations" : [
          |        {
          |          "timestamp" : 99,
          |          "value" : "cs",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 199,
          |          "value" : "cr",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ],
          |      "binaryAnnotations" : [ ]
          |    }
          |  ]
          |]
        """.stripMargin)
  }

  "find traces by annotation name" in {
    app.injector.instance[SpanStore].apply(allSpans)

    server.httpGet(
      path = "/api/v1/traces?serviceName=service3&annotationQuery=annotation",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  [
          |    {
          |      "traceId" : "0000000000000005",
          |      "name" : "otherMethod",
          |      "id" : "000000000000029a",
          |      "parentId" : "0000000000000002",
          |      "annotations" : [
          |        {
          |          "timestamp" : 60,
          |          "value" : "cs",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 65,
          |          "value" : "annotation",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 100,
          |          "value" : "cr",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ],
          |      "binaryAnnotations" : [
          |        {
          |          "key" : "annotation",
          |          "value" : "ann",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "key" : "binary",
          |          "value" : "YW5u",
          |          "type" : "BYTES",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ]
          |    }
          |  ]
          |]
        """.stripMargin)
  }

  "find traces by annotation name and value" in {
    app.injector.instance[SpanStore].apply(allSpans)

    server.httpGet(
      path = "/api/v1/traces?serviceName=service3&annotationQuery=annotation%3Dann",
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  [
          |    {
          |      "traceId" : "0000000000000005",
          |      "name" : "otherMethod",
          |      "id" : "000000000000029a",
          |      "parentId" : "0000000000000002",
          |      "annotations" : [
          |        {
          |          "timestamp" : 60,
          |          "value" : "cs",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 65,
          |          "value" : "annotation",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "timestamp" : 100,
          |          "value" : "cr",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ],
          |      "binaryAnnotations" : [
          |        {
          |          "key" : "annotation",
          |          "value" : "ann",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        },
          |        {
          |          "key" : "binary",
          |          "value" : "YW5u",
          |          "type" : "BYTES",
          |          "endpoint" : {
          |            "serviceName" : "service3",
          |            "ipv4" : "0.0.1.89",
          |            "port" : 345
          |          }
          |        }
          |      ]
          |    }
          |  ]
          |]
        """.stripMargin)
  }

  "find dependencies starting at timestamp zero" in {
    when(dependencyStore.getDependencies(Some(0), Some(deps.endTime))) thenReturn Future.value(deps)

    server.httpGet(
      path = "/api/v1/dependencies/0/" + deps.endTime,
      andExpect = Ok,
      withJsonBody =
        """
          |[
          |  {
          |    "parent" : "tfe",
          |    "child" : "mobileweb",
          |    "callCount" : 1
          |  },
          |  {
          |    "parent" : "Gizmoduck",
          |    "child" : "tflock",
          |    "callCount" : 2
          |  }
          |]
        """.stripMargin)
  }

  "find dependencies when empty" in {
    when(dependencyStore.getDependencies(Some(0), Some(deps.endTime))) thenReturn Future(Dependencies.zero)

    server.httpGet(
      path = "/api/v1/dependencies/0/" + deps.endTime,
      andExpect = Ok,
      withJsonBody = "[ ]")
  }
}