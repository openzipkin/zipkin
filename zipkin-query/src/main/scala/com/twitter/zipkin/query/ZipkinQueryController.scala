package com.twitter.zipkin.query

import com.twitter.finagle.httpx.Request
import com.twitter.finagle.tracing.SpanId
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.util.Future
import com.twitter.zipkin.json.JsonSpan
import com.twitter.zipkin.query.adjusters.TimeSkewAdjuster
import com.twitter.zipkin.storage.{DependencyStore, SpanStore}
import javax.inject.Inject

class ZipkinQueryController @Inject()(spanStore: SpanStore,
                                      dependencyStore: DependencyStore,
                                      queryExtractor: QueryExtractor,
                                      traceIds: QueryTraceIds,
                                      response: ResponseBuilder) extends Controller {

  private[this] val EmptyTraces = Future.value(Seq.empty[Seq[JsonSpan]])

  get("/api/v1/services") { request: Request =>
    spanStore.getAllServiceNames()
  }

  get("/api/v1/traces") { request: Request =>
    queryExtractor(request) match {
      case Some(qr) => traceIds(qr).flatMap(getTraces(_, qr.adjustClockSkew))
      case None => Future.value(response.badRequest())
    }
  }

  get("/api/v1/trace/:id") { request: GetTraceRequest =>
    getTraces(SpanId.fromString(request.id).map(_.toLong).toSeq, request.adjust_clock_skew).map(_.headOption)
  }

  get("/api/v1/dependencies/:from/:to") { request: GetDependenciesRequest =>
    dependencyStore.getDependencies(Some(request.from), Some(request.to)).map(_.links)
  }

  private[this] def getTraces(ids: Seq[Long], adjustClockSkew: Boolean): Future[Seq[Seq[JsonSpan]]] = {
    if (ids.isEmpty) return EmptyTraces
    spanStore.getSpansByTraceIds(ids).map { spans =>
      val traces = spans.map(Trace(_))
      if (adjustClockSkew) {
        traces.map(t => timeSkewAdjuster.adjust(_))
      }
      traces.map(_.spans.map(JsonSpan))
    }
  }

  private[this] val timeSkewAdjuster = new TimeSkewAdjuster()
}

case class GetDependenciesRequest(@RouteParam from: Long, @RouteParam to: Long)

case class GetTraceRequest(@RouteParam id: String, @QueryParam adjust_clock_skew: Boolean = true)
