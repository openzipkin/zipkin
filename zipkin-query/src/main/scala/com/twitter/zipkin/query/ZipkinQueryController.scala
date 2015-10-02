package com.twitter.zipkin.query

import com.twitter.finagle.httpx.Request
import com.twitter.finagle.tracing.SpanId
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.util.Future
import com.twitter.zipkin.common.{Span, Trace}
import com.twitter.zipkin.json.JsonSpan
import com.twitter.zipkin.query.adjusters.TimeSkewAdjuster
import com.twitter.zipkin.storage.{DependencyStore, SpanStore}
import javax.inject.Inject

class ZipkinQueryController @Inject()(spanStore: SpanStore,
                                      dependencyStore: DependencyStore,
                                      queryExtractor: QueryExtractor,
                                      response: ResponseBuilder) extends Controller {

  get("/api/v1/spans") { request: GetSpanNamesRequest =>
    spanStore.getSpanNames(request.serviceName)
  }

  get("/api/v1/services") { request: Request =>
    spanStore.getAllServiceNames()
  }

  get("/api/v1/traces") { request: Request =>
    queryExtractor(request) match {
      case Some(qr) => spanStore.getTraces(qr).map(adjustTimeskewAndRenderJson(_))
      case None => Future.value(response.badRequest)
    }
  }

  get("/api/v1/trace/:id") { request: GetTraceRequest =>
    val traceId = SpanId.fromString(request.id).map(_.toLong)
    if (traceId.isDefined) {
      spanStore.getTracesByIds(traceId.toSeq)
        .map(adjustTimeskewAndRenderJson(_))
        .map(_.headOption.getOrElse(response.notFound))
    } else {
      Future.value(response.notFound)
    }
  }

  get("/api/v1/dependencies/:from/:to") { request: GetDependenciesRequest =>
    dependencyStore.getDependencies(Some(request.from), Some(request.to)).map(_.links)
  }

  private[this] def adjustTimeskewAndRenderJson(spans: Seq[Seq[Span]]): Seq[List[JsonSpan]] = {
    spans.map(Trace(_))
      .map(timeSkewAdjuster.adjust)
      .map(_.spans.map(JsonSpan))
  }

  private[this] val timeSkewAdjuster = new TimeSkewAdjuster()
}

case class GetSpanNamesRequest(@QueryParam serviceName: String)

case class GetDependenciesRequest(@RouteParam from: Long, @RouteParam to: Long)

case class GetTraceRequest(@RouteParam id: String)
