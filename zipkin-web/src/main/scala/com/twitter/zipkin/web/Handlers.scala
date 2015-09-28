package com.twitter.zipkin.web


import com.google.common.io.ByteStreams
import com.twitter.finagle.httpx.{ParamMap, Request, Response}
import com.twitter.finagle.stats.{Stat, StatsReceiver}
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Filter, Service}
import com.twitter.finatra.httpclient.HttpClient
import com.twitter.io.Buf
import com.twitter.util.Future
import com.twitter.zipkin.json._
import java.io.{File, FileInputStream, InputStream}
import com.twitter.zipkin.query._
import com.twitter.zipkin.web.mustache.ZipkinMustache
import com.twitter.zipkin.{Constants => ZConstants}
import org.jboss.netty.handler.codec.http.QueryStringEncoder

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet

class Handlers(mustacheGenerator: ZipkinMustache, queryExtractor: QueryExtractor) {
  import Util._

  type Renderer = (Response => Unit)

  case class CopyRenderer(input: Response) extends Renderer {
    def apply(response: Response) {
      response.contentString = input.contentString
      response.statusCode = input.statusCode
    }
  }

  case class ErrorRenderer(code: Int, msg: String) extends Renderer {
    def apply(response: Response) {
      response.contentString = msg
      response.statusCode = code
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
      response.content = Buf.ByteArray.Owned(content)
    }
  }

  private[this] val EmptyTraces = Future.value(Seq.empty[TraceSummary])
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

  def addLayout(pageTitle: String, environment: String): Filter[Request, Renderer, Request, Renderer] =
    Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
      svc(req) map { renderer =>
        response: Response => {
          renderer(response)
          val data = Map[String, Object](
            ("pageTitle" -> pageTitle),
            ("environment" -> environment),
            ("body" -> response.contentString))
          val r = MustacheRenderer("v2/layout.mustache", data)
          r(response)
        }
      }
    }

  def handlePublic(
    resourceDirs: Set[String],
    typesMap: Map[String, String],
    docRoot: Option[String] = None
  ) =
    new Service[Request, Renderer] {
      private[this] var rendererCache = Map.empty[String, Future[Renderer]]

      private[this] def getStream(path: String): Option[InputStream] =
        docRoot map { root =>
          new FileInputStream(new File(root, path))
        } orElse {
          Option(getClass.getResourceAsStream(path)) filter { _.available > 0 }
        }

      private[this] def getRenderer(path: String): Option[Future[Renderer]] = {
        rendererCache.get(path) orElse {
          synchronized {
            rendererCache.get(path) orElse {
              resourceDirs find(path.startsWith) flatMap { _ =>
                  val typ = typesMap find { case (n, _) => path.endsWith(n) } map { _._2 } getOrElse("text/plain")
                   getStream(path) map { input =>
                  val renderer = Future.value(StaticRenderer(input, typ))
                  if (docRoot.isEmpty) rendererCache += (path -> renderer)
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
    startTime: String,
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
      val start = stamps.map(_.startTimestamp).min
      val ts = stamps.find(_.startTimestamp == start).get
      val (current, next) = stamps.partition { t => t.startTimestamp >= ts.startTimestamp && t.endTimestamp <= ts.endTimestamp }
      val end = current.map(_.endTimestamp).max
      totalServiceTime(next, acc + (end - start))
    }

  private[this] def traceSummaryToMustache(
    serviceName: Option[String],
    ts: Seq[TraceSummary]
  ): Map[String, Any] = {
    val (maxDuration, minStartTime, maxStartTime) = ts.foldLeft((Long.MinValue, Long.MaxValue, Long.MinValue)) { case ((maxD, minST, maxST), t) =>
      ( math.max(t.durationMicro / 1000, maxD),
        math.min(t.startTimestamp, minST),
        math.max(t.startTimestamp, maxST)
      )
    }

    val traces = ts.map { t =>
      val duration = t.durationMicro / 1000
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
        queryExtractor.fmt.format(new java.util.Date(t.startTimestamp / 1000)),
        t.startTimestamp,
        duration,
        durationStr(t.durationMicro.toLong * 1000),
        serviceTime.map { st => ((st.toFloat / t.durationMicro.toFloat) * 100).toInt }.getOrElse(0),
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
      val qResults = serviceName match {
        case Some(service) => route[Seq[Seq[JsonSpan]]](client, "/api/v1/traces", req.params)
          .map(traces => traces.map(_.map(JsonSpan.invert))
          .map(Trace.apply(_)).flatMap(TraceSummary(_).toSeq))
        case None => EmptyTraces
      }
      val spanResults = serviceName match {
        case Some(service) => client.executeJson[SortedSet[String]](Request(s"/api/v1/traces?serviceName=${serviceName}"))
        case None => Future.value(Seq.empty[String])
      }
      val serviceResults = client.executeJson[SortedSet[String]](Request(s"/api/v1/services"))
      for (services <- serviceResults; results <- qResults; spans <- spanResults) yield {
        val svcList = services.toList.sorted map {
          svc => Map("name" -> svc, "selected" -> (if (Some(svc) == serviceName) "selected" else ""))
        }
        val spanList = spans.toList.sorted map {
          span => Map("name" -> span, "selected" -> (if (Some(span) == spanName) "selected" else ""))
        }

        var data = Map[String, Object](
          ("serviceName" -> serviceName),
          ("timestamp" -> queryExtractor.getTimestampStr(req)),
          ("annotationQuery" -> req.params.get("annotationQuery").getOrElse("")),
          ("services" -> svcList),
          ("spans" -> spanList),
          ("limit" -> queryExtractor.getLimitStr(req)))

        queryExtractor(req) foreach { qReq =>
          data ++= Map(
            ("queryResults" -> traceSummaryToMustache(serviceName, results)),
            ("annotations" -> qReq.annotations),
            ("binaryAnnotations" -> qReq.binaryAnnotations))
        }
        MustacheRenderer("v2/index.mustache", data)
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

  def handleDependency(): Service[Request, MustacheRenderer] =
    Service.mk[Request, MustacheRenderer] { req =>
      Future(MustacheRenderer("v2/dependency.mustache", Map[String, Object]()))
    }

  private[this] def pathTraceId(id: Option[String]): Option[SpanId] =
    id.flatMap(SpanId.fromString(_))

  trait NotFoundService extends Service[Request, Renderer] {
    def process(req: Request): Option[Future[Renderer]]

    def apply(req: Request): Future[Renderer] =
      process(req) getOrElse NotFound
  }

  private[this] def renderTrace(trace: Trace): Renderer = {
    val traceStartTimestamp = trace.getStartAndEndTimestamp.map(_.start).getOrElse(0L)
    val spanDepths = trace.toSpanDepths
    val childMap = trace.getIdToChildrenMap
    val spanMap = trace.getIdToSpanMap

    val spans = for {
      rootSpan <- trace.getRootSpans()
      span <- trace.getSpanTree(rootSpan, childMap).toList
    } yield {
      val start = span.firstAnnotation.map(_.timestamp).getOrElse(traceStartTimestamp)

      val depth = trace.toSpanDepths.get.getOrElse(span.id, 1)
      val width = span.duration.map { d => (d.toDouble / trace.duration.toDouble) * 100 }.getOrElse(0.0)

      val binaryAnnotations = span.binaryAnnotations.map {
        case ann if ZConstants.CoreAddress.contains(ann.key) =>
          val key = ZConstants.CoreAnnotationNames.get(ann.key).get
          val value = ann.host.map { e => s"${e.getHostAddress}:${e.getUnsignedPort}" }.get
          JsonBinaryAnnotation(key, value, None, ann.host.map(JsonService))
        case ann => JsonBinaryAnnotation(ann)
      }

      Map(
        "spanId" -> SpanId(span.id).toString,
        "parentId" -> span.parentId.filter(spanMap.get(_).isDefined).map(SpanId(_).toString),
        "spanName" -> span.name,
        "serviceNames" -> span.serviceNames.mkString(","),
        "serviceName" -> span.serviceName,
        "duration" -> span.duration,
        "durationStr" -> span.duration.map { d => durationStr(d * 1000) },
        "left" -> ((start - traceStartTimestamp).toFloat / trace.duration.toFloat) * 100,
        "width" -> (if (width < 0.1) 0.1 else width),
        "depth" -> (depth + 1) * 5,
        "depthClass" -> (depth - 1) % 6,
        "children" -> childMap.get(span.id).map(_.map(s => SpanId(s.id).toString).mkString(",")),
        "annotations" -> span.annotations.map { a =>
          Map(
            "isCore" -> ZConstants.CoreAnnotations.contains(a.value),
            "left" -> span.duration.map { d => ((a.timestamp - start).toFloat / d.toFloat) * 100 },
            "endpoint" -> a.host.map { e => s"${e.getHostAddress}:${e.getUnsignedPort}" },
            "value" -> annoToString(a.value),
            "timestamp" -> a.timestamp,
            "relativeTime" -> durationStr((a.timestamp - traceStartTimestamp) * 1000),
            "serviceName" -> a.host.map(_.serviceName),
            "width" -> 8
          )
        },
        "binaryAnnotations" -> binaryAnnotations
      )
    }

    val traceDuration = trace.duration * 1000
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
      "depth" -> spanDepths.map(_.values.max),
      "totalSpans" -> spans.size.asInstanceOf[Object],
      "serviceCounts" -> serviceDurations.map(_.sortBy(_.name)),
      "timeMarkers" -> timeMarkers,
      "timeMarkersBackup" -> timeMarkersBackup,
      "spans" -> spans,
      "spansBackup" -> spansBackup)

    MustacheRenderer("v2/trace.mustache", data)
  }

  def handleTraces(client: HttpClient): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      pathTraceId(req.path.split("/").lastOption) map { id =>
        client.executeJson[Seq[JsonSpan]](Request("/api/v1/trace/" + id))
          .map(_.map(JsonSpan.invert))
          .map(Trace.apply(_))
          .map(renderTrace(_))
      } getOrElse NotFound
    }
}
