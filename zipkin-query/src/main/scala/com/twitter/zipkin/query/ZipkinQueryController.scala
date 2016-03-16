package com.twitter.zipkin.query

import com.fasterxml.jackson.core.`type`.TypeReference
import com.twitter.finagle.http.{Fields, Request}
import com.twitter.finagle.tracing.SpanId
import com.twitter.finatra.annotations.Flag
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.request.QueryParam
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.zipkin.Constants.MaxServicesWithoutCaching
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift.thriftListToSpans
import com.twitter.zipkin.json.{JsonSpan, ZipkinJson}
import com.twitter.zipkin.storage.{DependencyStore, SpanStore}
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ZipkinQueryController @Inject()(spanStore: SpanStore,
                                      dependencyStore: DependencyStore,
                                      queryExtractor: QueryExtractor,
                                      response: ResponseBuilder,
                                      @Flag("zipkin.queryService.servicesMaxAge") servicesMaxAge: Int,
                                      @Flag("zipkin.queryService.limit") val queryLimit: Int,
                                      @Flag("zipkin.queryService.environment") val environment: String,
                                      @Flag("zipkin.queryService.lookback") val defaultLookback: Long) extends Controller {

  get("/health") { request: Request =>
    "OK\n" // TODO: expose SpanStore.ok? or similar per #994
  }

  get("/config.json") { request: Request =>
    response.ok(Map(
      "environment" -> environment,
      "queryLimit" -> queryLimit,
      "defaultLookback" -> defaultLookback
    )).contentType("application/json")
  }

  post("/api/v1/spans") { request: Request =>
    val contentEncoding = Option(request.headerMap.get(Fields.ContentEncoding))

    // gunzip here until we can do it with netty: https://github.com/twitter/finagle/issues/469
    val gzipped = contentEncoding.map(c => c.contains("gzip")).getOrElse(false)
    var bytes = new Array[Byte](request.content.length)
    request.content.write(bytes, 0)
    if (gzipped) {
      bytes = gunzip(bytes)
    }

    val spans: Try[List[Span]] = try {
      Success(request.mediaType match {
        case Some("application/x-thrift") => thriftListToSpans(bytes)
        case _ => jsonSpansReader.readValue(bytes)
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

  get("/api/v1/trace/:id") { request: Request =>
    val traceIdText = request.path.replace("/api/v1/trace/", "")
    val traceId = SpanId.fromString(traceIdText).map(_.toLong)
    if (traceId.isDefined) {
      // manually parsing request as @QueryParam doesn't support no value
      val spans = if (request.params.get("raw").isDefined) {
        spanStore.getSpansByTraceIds(traceId.toSeq)
      } else {
        spanStore.getTracesByIds(traceId.toSeq)
      }
      spans.map(_.map(_.map(JsonSpan)))
           .map(_.headOption.getOrElse(response.notFound))
    } else {
      Future.value(response.notFound)
    }
  }

  get("/api/v1/dependencies") { request: GetDependenciesRequest =>
    dependencyStore.getDependencies(request.endTs, request.lookback.orElse(Some(defaultLookback)))
  }

  get("/:*") { request: Request =>
    response.ok.fileOrIndex(
    request.params("*"),
    "index.html"
    )
  }

  val jsonSpansReader = ZipkinJson.readerFor(new TypeReference[Seq[JsonSpan]] {})

  private def gunzip(bytes: Array[Byte]) = { // array in lieu of a gunzip Buf utility
    val gis = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try {
      StreamIO.buffer(gis).toByteArray
    } finally {
      gis.close()
    }
  }
}

case class GetSpanNamesRequest(@QueryParam serviceName: String)

case class GetDependenciesRequest(@QueryParam endTs: Long, @QueryParam lookback: Option[Long])
