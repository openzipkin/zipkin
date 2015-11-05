package com.twitter.zipkin.query

import com.fasterxml.jackson.core.`type`.TypeReference
import com.twitter.finagle.http.Request
import com.twitter.finagle.tracing.SpanId
import com.twitter.finatra.annotations.Flag
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.util.Future
import com.twitter.zipkin.Constants.MaxServicesWithoutCaching
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift.thriftListToSpans
import com.twitter.zipkin.json.{JsonSpan, ZipkinJson}
import com.twitter.zipkin.storage.{DependencyStore, SpanStore}
import javax.inject.Inject
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ZipkinQueryController @Inject()(spanStore: SpanStore,
                                      dependencyStore: DependencyStore,
                                      queryExtractor: QueryExtractor,
                                      response: ResponseBuilder,
                                      @Flag("zipkin.queryService.servicesMaxAge") servicesMaxAge: Int) extends Controller {

  post("/api/v1/spans") { request: Request =>
    val spans: Try[List[Span]] = try {
      Success(request.mediaType match {
        case Some("application/x-thrift") => {
          val bytes = new Array[Byte](request.content.length)
          request.content.write(bytes, 0)
          thriftListToSpans(bytes)
        }
        case _ => jsonSpansReader.readValue(request.contentString)
          .asInstanceOf[List[JsonSpan]]
          .map(JsonSpan.invert(_))
      })
    } catch {
      case NonFatal(e) => Failure(e)
    }
    spans match {
      case Failure(exception) => response.badRequest(exception.getMessage)
      case Success(spans) => spanStore.apply(spans); response.accepted // returning fast is intentional
    }
  }

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
      case Success(qr) => spanStore.getTraces(qr).map(_.map(_.map(JsonSpan)))
      case Failure(ex) => Future.value(response.badRequest(ex.getMessage))
    }
  }

  get("/api/v1/trace/:id") { request: GetTraceRequest =>
    val traceId = SpanId.fromString(request.id).map(_.toLong)
    if (traceId.isDefined) {
      spanStore.getTracesByIds(traceId.toSeq)
        .map(_.map(_.map(JsonSpan)))
        .map(_.headOption.getOrElse(response.notFound))
    } else {
      Future.value(response.notFound)
    }
  }

  get("/api/v1/dependencies") { request: GetDependenciesRequest =>
    dependencyStore.getDependencies(Some(request.startTs), Some(request.endTs))
  }

  val jsonSpansReader = ZipkinJson.reader(new TypeReference[Seq[JsonSpan]] {})
}

case class GetSpanNamesRequest(@QueryParam serviceName: String)

case class GetDependenciesRequest(@QueryParam startTs: Long, @QueryParam endTs: Long)

case class GetTraceRequest(@RouteParam id: String)
