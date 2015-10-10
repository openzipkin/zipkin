package com.twitter.zipkin.query

import com.twitter.finagle.httpx.Request
import com.twitter.finagle.tracing.SpanId
import com.twitter.finatra.annotations.Flag
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.util.Future
import com.twitter.zipkin.Constants.MaxServicesWithoutCaching
import com.twitter.zipkin.common.{Span, Trace}
import com.twitter.zipkin.json.JsonSpan
import com.twitter.zipkin.query.adjusters.TimeSkewAdjuster
import com.twitter.zipkin.storage.{DependencyStore, SpanStore}
import javax.inject.Inject
import scala.util.{Failure, Success}

class ZipkinQueryController @Inject()(spanStore: SpanStore,
                                      dependencyStore: DependencyStore,
                                      queryExtractor: QueryExtractor,
                                      response: ResponseBuilder,
                                      @Flag("zipkin.queryService.servicesMaxAge") servicesMaxAge: Int) extends Controller {

  get("/api/v1/spans") { request: GetSpanNamesRequest =>
    spanStore.getSpanNames(request.serviceName)
  }

  get("/api/v1/services") { request: Request =>
    spanStore.getAllServiceNames() map { serviceNames =>
      if (serviceNames.size <= MaxServicesWithoutCaching) {
        response.ok(serviceNames)
      } else {
        response.ok(serviceNames).header("Cache-Control", s"max-age=${servicesMaxAge}, must-revalidate")
      }
    };
  }

  get("/api/v1/traces") { request: Request =>
    queryExtractor(request) match {
      case Success(qr) => spanStore.getTraces(qr).map(adjustTimeskewAndRenderJson(_))
      case Failure(ex) => Future.value(response.badRequest(ex.getMessage))
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

  get("/api/v1/dependencies") { request: GetDependenciesRequest =>
    dependencyStore.getDependencies(Some(request.startTs), Some(request.endTs))
  }

  private[this] def adjustTimeskewAndRenderJson(spans: Seq[Seq[Span]]): Seq[List[JsonSpan]] = {
    spans.map(Trace(_))
      .map(timeSkewAdjuster.adjust)
      .map(_.spans.map(JsonSpan))
  }

  private[this] val timeSkewAdjuster = new TimeSkewAdjuster()
}

case class GetSpanNamesRequest(@QueryParam serviceName: String)

case class GetDependenciesRequest(@QueryParam startTs: Long, @QueryParam endTs: Long)

case class GetTraceRequest(@RouteParam id: String)
