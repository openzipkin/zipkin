package com.twitter.zipkin.web

import com.google.common.io.ByteStreams
import com.twitter.finagle.http.{ParamMap, Request, Response}
import com.twitter.finagle.stats.{Stat, StatsReceiver}
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Filter, Service}
import com.twitter.finatra.httpclient.HttpClient
import com.twitter.io.Buf
import com.twitter.util.{Future, TwitterDateFormat}
import com.twitter.zipkin.common._
import com.twitter.zipkin.json._
import com.twitter.zipkin.web.mustache.ZipkinMustache
import com.twitter.zipkin.{Constants => ZConstants}
import com.twitter.conversions.time._
import org.jboss.netty.handler.codec.http.QueryStringEncoder
import java.io.InputStream

import scala.annotation.tailrec

class Handlers(mustacheGenerator: ZipkinMustache, queryExtractor: QueryExtractor) {
  private[this] val fmt = TwitterDateFormat("MM-dd-yyyy'T'HH:mm:ss.SSSZ")

  import Util._

  type Renderer = (Response => Unit)

  case class CopyRenderer(input: Response) extends Renderer {
    def apply(response: Response) {
      response.contentString = input.contentString
      response.statusCode = input.statusCode
      input.headerMap foreach (e => response.headerMap.put(e._1,e._2))
    }
  }

  case class ErrorRenderer(code: Int, msg: String) extends Renderer {
    def apply(response: Response) {
      response.contentString = msg
      response.statusCode = code
    }
  }

  case class ConfigRenderer(config: Map[String, _]) extends Renderer {
    def apply(response: Response) {
      response.contentType = "application/javascript"
      response.contentString = s"window.config = ${ZipkinJson.writeValueAsString(config)};"
    }
  }

  case class JsonRenderer(config: Map[String, _]) extends Renderer {
    def apply(response: Response) {
      response.contentType = "application/json"
      response.contentString = ZipkinJson.writeValueAsString(config)
    }
  }

  case class MustacheRenderer(template: String, data: Map[String, Object]) extends Renderer {
    def apply(response: Response) {
      response.contentType = "text/html"
      response.contentString = generate
    }

    def generate = mustacheGenerator.render(template, data)
  }

  case class StaticRenderer(input: InputStream, typ: String) extends Renderer {
    private[this] val content = {
      val bytes = ByteStreams.toByteArray(input)
      input.close()
      bytes
    }

    def apply(response: Response) {
      response.setContentType(typ)
      response.cacheControl = 10.minutes
      response.content = Buf.ByteArray.Owned(content)
    }
  }

  private[this] val EmptyTraces = Future.value(Seq.empty[TraceSummary])
  private[this] val EmptyStrings = Future.value(Seq.empty[String])
  private[this] val NotFound = Future.value(ErrorRenderer(404, "Not Found"))

  def collectStats(stats: StatsReceiver): Filter[Request, Response, Request, Response] =
    Filter.mk[Request, Response, Request, Response] { (req, svc) =>
      Stat.timeFuture(stats.stat("request"))(svc(req)) onSuccess { rep =>
        stats.scope("response").counter(rep.statusCode.toString).incr()
      }
    }

  val catchExceptions =
    Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
      svc(req) rescue { case thrown: Throwable =>
        val errorMsg = Option(thrown.getMessage).getOrElse("Unknown error")
        val stacktrace = Option(thrown.getStackTraceString).getOrElse("")
        Future.value(ErrorRenderer(500, errorMsg + "\n\n\n" + stacktrace))
      }
    }

  val renderPage =
    Filter.mk[Request, Response, Request, Renderer] { (req, svc) =>
      svc(req) map { renderer =>
        val res = req.response
        renderer(res)
        res.contentLength = res.content.length
        res
      }
    }

  def checkPath(path: List[String]): Filter[Request, Renderer, Request, Renderer] = {
    val requiredSize = path.takeWhile { !_.startsWith(":") }.size
    if (path.size == requiredSize) Filter.identity[Request, Renderer] else
      Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
        if (req.path.split("/").size >= path.size) svc(req) else NotFound
      }
  }

  def addLayout(): Filter[Request, Renderer, Request, Renderer] =
    Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
      svc(req) map { renderer =>
        response: Response => {
          renderer(response)
          val data = Map[String, Object](
            ("body" -> response.contentString))
          val r = MustacheRenderer("templates/v2/layout.mustache", data)
          r(response)
        }
      }
    }

  def handlePublic(
    resourceDirs: Set[String],
    typesMap: Map[String, String]) =
    new Service[Request, Renderer] {
      private[this] var rendererCache = Map.empty[String, Future[Renderer]]

      private[this] def getStream(path: String): Option[InputStream] =
          Option(getClass.getResourceAsStream(path)) filter { _.available > 0 }

      private[this] def getRenderer(path: String): Option[Future[Renderer]] = {
        rendererCache.get(path) orElse {
          synchronized {
            rendererCache.get(path) orElse {
              resourceDirs find(path.startsWith) flatMap { _ =>
                  val typ = typesMap find { case (n, _) => path.endsWith(n) } map { _._2 } getOrElse("text/plain")
                   getStream(path) map { input =>
                  val renderer = Future.value(StaticRenderer(input, typ))
                  rendererCache += (path -> renderer)
                  renderer
                }
              }
            }
          }
        }
      }

      def apply(req: Request): Future[Renderer] =
        if (req.path contains "..") NotFound else {
          getRenderer(req.path) getOrElse NotFound
        }
    }

  case class MustacheServiceDuration(name: String, count: Int, max: Long)
  case class MustacheTraceSummary(
    traceId: String,
    startTs: String,
    timestamp: Long,
    duration: Long,
    durationStr: String,
    servicePercentage: Int,
    spanCount: Int,
    serviceDurations: Seq[MustacheServiceDuration],
    width: Int)

  case class MustacheTraceId(id: String)

  @tailrec
  private[this] def totalServiceTime(stamps: Seq[SpanTimestamp], acc: Long = 0): Long =
    if (stamps.isEmpty) acc else {
      val ts = stamps.minBy(_.timestamp)
      val (current, next) = stamps.partition { t => t.timestamp >= ts.timestamp && t.endTs <= ts.endTs }
      val endTs = current.map(_.endTs).max
      totalServiceTime(next, acc + (endTs - ts.timestamp))
    }

  private[this] def traceSummaryToMustache(
    serviceName: Option[String],
    ts: Seq[TraceSummary]
  ): Map[String, Any] = {
    val maxDuration = ts.foldLeft(Long.MinValue) { case ((maxD), t) =>
      math.max(t.duration / 1000, maxD)
    }

    val traces = ts.map { t =>
      val duration = t.duration / 1000
      val groupedSpanTimestamps = t.spanTimestamps.groupBy(_.name)

      val serviceDurations = groupedSpanTimestamps.map { case (n, sts) =>
        MustacheServiceDuration(n, sts.length, sts.map(_.duration).max / 1000)
      }.toSeq

      val serviceTime = for {
        name <- serviceName
        timestamps <- groupedSpanTimestamps.get(name)
      } yield totalServiceTime(timestamps)

      MustacheTraceSummary(
        t.traceId,
        fmt.format(new java.util.Date(t.timestamp / 1000)),
        t.timestamp,
        duration,
        durationStr(t.duration),
        serviceTime.map { st => ((st.toFloat / t.duration.toFloat) * 100).toInt }.getOrElse(0),
        groupedSpanTimestamps.foldLeft(0) { case (acc, (_, sts)) => acc + sts.length },
        serviceDurations,
        ((duration.toFloat / maxDuration) * 100).toInt
      )
    }.sortBy(_.duration).reverse

    Map(
      ("traces" -> traces),
      ("count" -> traces.size))
  }

  def handleIndex(client: HttpClient): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val serviceName = req.params.get("serviceName").filterNot(_ == "")
      val spanName = req.params.get("spanName").filterNot(_ == "")

      val spansCall = serviceName match {
        case Some(service) => client.executeJson[Seq[String]](Request(s"/api/v1/spans?serviceName=${service}"))
        case None => EmptyStrings
      }

      // only call get traces if the user entered a query
      val tracesCall = serviceName match {
        case Some(service) => route[Seq[List[JsonSpan]]](client, "/api/v1/traces", req.params)
          .map(traces => traces.map(_.map(JsonSpan.invert))
          .flatMap(TraceSummary(_)))
        case None => EmptyTraces
      }

      for (spans <- spansCall; traces <- tracesCall) yield {
        val spanList = spans.toList map {
          span => Map("name" -> span, "selected" -> (if (Some(span) == spanName) "selected" else ""))
        }

        var data = Map[String, Object](
          ("serviceName" -> serviceName),
          ("endTs" -> queryExtractor.getTimestampStr(req)),
          ("annotationQuery" -> req.params.get("annotationQuery").getOrElse("")),
          ("spans" -> spanList),
          ("limit" -> queryExtractor.getLimitStr(req)),
          ("minDuration" -> req.params.get("minDuration").getOrElse("")))

        queryExtractor.getAnnotations(req).foreach( annos =>
          data ++= Map(
            ("queryResults" -> traceSummaryToMustache(serviceName, traces)),
            ("annotations" -> annos._1),
            ("binaryAnnotations" -> annos._2)))

        JsonRenderer(data)
      }
    }

  def handleRoute(client: HttpClient, baseUri: String): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val encoder = new QueryStringEncoder(baseUri)
      req.params.foreach { case (key, value) =>
        encoder.addParam(key, value)
      }
      client.execute(Request(encoder.toString)).map(CopyRenderer)
    }


  private[this] def route[T: Manifest](client: HttpClient, baseUri: String, params: ParamMap) = {
    val encoder = new QueryStringEncoder(baseUri)
    params.foreach { case (key, value) =>
      encoder.addParam(key, value)
    }
    client.executeJson[T](Request(encoder.toString))
  }

  def serveStaticIndex() : Service[Request, Renderer] =
    Service.mk[Request, MustacheRenderer] { reg =>
      Future(MustacheRenderer("templates/v2/layout.mustache", Map.empty))
    }

  def handleDependency(): Service[Request, MustacheRenderer] =
    Service.mk[Request, MustacheRenderer] { req =>
      Future(MustacheRenderer("templates/v2/dependency.mustache", Map[String, Object]()))
    }

  def handleConfig(env: Map[String, _]) : Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      Future(ConfigRenderer(env))
    }

  private[this] def pathTraceId(id: Option[String]): Option[SpanId] =
    id.flatMap(SpanId.fromString(_))

  trait NotFoundService extends Service[Request, Renderer] {
    def process(req: Request): Option[Future[Renderer]]

    def apply(req: Request): Future[Renderer] =
      process(req) getOrElse NotFound
  }

  private[this] def renderTrace(trace: List[Span]): Renderer = {
    val traceTimestamp = trace.headOption.flatMap(_.timestamp).getOrElse(0L)
    val traceDuration = Trace.duration(trace).getOrElse(0L)
    val spanDepths = TraceSummary.toSpanDepths(trace)
    val spanMap = getIdToSpanMap(trace)

    val spans = for {
      rootSpan <- getRootSpans(trace)
      span <- SpanTreeEntry.create(rootSpan, trace).toList
    } yield {
      val spanStartTs = span.timestamp.getOrElse(traceTimestamp)

      val depth = spanDepths.getOrElse(span.id, 1)
      val width = span.duration.map { d => (d.toDouble / traceDuration.toDouble) * 100 }.getOrElse(0.0)

      var binaryAnnotations = span.binaryAnnotations.map {
        case ann if ZConstants.CoreAddress.contains(ann.key) =>
          ann.host.map(toHostAndPort(ZConstants.CoreAnnotationNames.get(ann.key).get, _))
        case ann if ZConstants.CoreAnnotationNames.contains(ann.key) =>
          JsonBinaryAnnotation(ann.copy(key = ZConstants.CoreAnnotationNames.get(ann.key).get))
        case ann => JsonBinaryAnnotation(ann)
      }
      span.binaryAnnotations.find(_.key == ZConstants.LocalComponent).foreach { ann =>
        binaryAnnotations ++= ann.host.map(toHostAndPort("Local Address", _))
      }

      Map(
        "spanId" -> SpanId(span.id).toString,
        "parentId" -> span.parentId.filter(spanMap.get(_).isDefined).map(SpanId(_).toString),
        "spanName" -> span.name,
        "serviceNames" -> span.serviceNames.mkString(","),
        "serviceName" -> span.serviceName.getOrElse(""),
        "duration" -> span.duration,
        "durationStr" -> span.duration.map(durationStr),
        "left" -> ((spanStartTs - traceTimestamp).toFloat / traceDuration.toFloat) * 100,
        "width" -> (if (width < 0.1) 0.1 else width),
        "depth" -> (depth + 1) * 5,
        "depthClass" -> (depth - 1) % 6,
        "children" -> spanMap.get(span.id).map(s => SpanId(s.id).toString).mkString(","),
        "annotations" -> span.annotations.map { a =>
          Map(
            "isCore" -> ZConstants.CoreAnnotations.contains(a.value),
            "left" -> span.duration.map { d => ((a.timestamp - spanStartTs).toFloat / d.toFloat) * 100 },
            "endpoint" -> a.host.map { e => s"${e.getHostAddress}:${e.getUnsignedPort}" },
            "value" -> annoToString(a.value),
            "timestamp" -> a.timestamp,
            "relativeTime" -> durationStr((a.timestamp - traceTimestamp)),
            "serviceName" -> a.host.map(_.serviceName),
            "width" -> 8
          )
        },
        "binaryAnnotations" -> binaryAnnotations
      )
    }

    val serviceDurations = TraceSummary(trace) map { summary =>
      summary.spanTimestamps.groupBy(_.name).map { case (n, sts) =>
        MustacheServiceDuration(n, sts.length, sts.map(_.duration).max / 1000)
      }.toSeq
    }

    val timeMarkers = Seq[Double](0.0, 0.2, 0.4, 0.6, 0.8, 1.0).zipWithIndex map { case (p, i) =>
      Map("index" -> i, "time" -> durationStr((traceDuration * p).toLong))
    }

    val timeMarkersBackup = timeMarkers.map { m => collection.mutable.Map() ++ m }
    val spansBackup = spans.map { m => collection.mutable.Map() ++ m }

    val data = Map[String, Object](
      "duration" -> durationStr(traceDuration),
      "services" -> serviceDurations.map(_.size),
      "depth" -> spanDepths.values.reduceOption(_ max _),
      "totalSpans" -> spans.size.asInstanceOf[Object],
      "serviceCounts" -> serviceDurations.map(_.sortBy(_.name)),
      "timeMarkers" -> timeMarkers,
      "timeMarkersBackup" -> timeMarkersBackup,
      "spans" -> spans,
      "spansBackup" -> spansBackup)

    JsonRenderer(data)
  }

  def handleTrace(client: HttpClient): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      pathTraceId(req.path.split("/").lastOption) map { id =>
        client.execute(Request(s"/api/v1/trace/$id"))
          .map(CopyRenderer)
      } getOrElse NotFound
    }

  def handleTraces(client: HttpClient): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      pathTraceId(req.path.split("/").lastOption) map { id =>
        client.executeJson[List[JsonSpan]](Request(s"/api/v1/trace/$id"))
          .map(_.map(JsonSpan.invert))
          .map(renderTrace(_))
      } getOrElse NotFound
    }

  private def toHostAndPort(key: String, endpoint: Endpoint): JsonBinaryAnnotation = {
    val value = s"${endpoint.getHostAddress}:${endpoint.getUnsignedPort}"
    JsonBinaryAnnotation(key, value, None, Some(JsonEndpoint(endpoint)))
  }
}
