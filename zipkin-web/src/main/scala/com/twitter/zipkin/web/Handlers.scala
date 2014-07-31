package com.twitter.zipkin.web

import com.twitter.common.stats.ApproximateHistogram
import com.twitter.conversions.time._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Filter, Service, SimpleFilter}
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.Constants.CoreAnnotations
import com.twitter.zipkin.common.json._
import com.twitter.zipkin.common.mustache.ZipkinMustache
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.gen.{Adjust, TraceCombo, ZipkinQuery}
import com.twitter.zipkin.query.{TraceSummary, QueryRequest}
import java.io.{File, FileInputStream, InputStream}
import java.text.SimpleDateFormat
import org.apache.commons.io.IOUtils
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

class Handlers(jsonGenerator: ZipkinJson, mustacheGenerator: ZipkinMustache) {
  import Util._

  type Renderer = (Response => Unit)

  case class ErrorRenderer(code: Int, msg: String) extends Renderer {
    def apply(response: Response) {
      response.contentString = msg
      response.statusCode = code
    }
  }

  case class MustacheRenderer(template: String, data: Map[String, Object]) extends Renderer {
    def apply(response: Response) {
      response.contentString = generate
    }

    def generate = mustacheGenerator.render(template, data)
  }

  case class JsonRenderer(data: Any) extends Renderer {
    def apply(response: Response) {
      response.setContentTypeJson()
      response.contentString = jsonGenerator.generate(data)
    }
  }

  case class StaticRenderer(input: InputStream, typ: String) extends Renderer {
    private[this] val content = {
      val bytes = IOUtils.toByteArray(input)
      input.read(bytes)
      bytes
    }

    def apply(response: Response) {
      response.setContentType(typ)
      response.content = ChannelBuffers.wrappedBuffer(content)
    }
  }

  private[this] val EmptyTraces = Future.value(Seq.empty[TraceSummary])
  private[this] val NotFound = Future.value(ErrorRenderer(404, "Not Found"))
  private[this] val EmptyRealtimeAgg = Future.value(Map.empty[String, Seq[Long]])

  private[this] def query(
    client: ZipkinQuery[Future],
    queryRequest: QueryRequest,
    request: Request,
    retryLimit: Int = 10
  ): Future[Seq[TraceSummary]] = {
    client.getTraceIds(queryRequest.toThrift) flatMap { resp =>
      resp.traceIds match {
        case Nil =>
          EmptyTraces

        case ids =>
          val adjusters = getAdjusters(request)
          client.getTraceSummariesByIds(ids, adjusters) map { _.map { _.toTraceSummary } }
      }
    }
  }

  private[this] def querySpanDurantions(
    client: ZipkinQuery[Future],
    queryRequest: QueryRequest
  ): Future[collection.Map[String, Seq[Long]]] = {
    client.getSpanDurations(
      queryRequest.endTs,
      queryRequest.serviceName,
      queryRequest.spanName.getOrElse("")
    )
  }

  private[this] def queryServiceNamesToTraceIds(
    client: ZipkinQuery[Future],
    queryRequest: QueryRequest
  ): Future[collection.Map[String, Seq[Long]]] = {
    client.getServiceNamesToTraceIds(
      queryRequest.endTs,
      queryRequest.serviceName,
      queryRequest.spanName.getOrElse("")
    )
  }

  private[this] def getServices(client: ZipkinQuery[Future]): Future[Seq[String]] =
    client.getServiceNames() map { _.toSeq.sorted }

  /**
   * Returns a sequence of adjusters based on the params for a request. Default is TimeSkewAdjuster
   */
  private[this] def getAdjusters(request: Request) =
    request.params.get("adjust_clock_skew") match {
      case Some("false") => Seq.empty[Adjust]
      case _ => Seq(Adjust.TimeSkew)
    }

  val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  def collectStats(stats: StatsReceiver): Filter[Request, Response, Request, Response] =
    Filter.mk[Request, Response, Request, Response] { (req, svc) =>
      stats.timeFuture("request")(svc(req)) onSuccess { rep =>
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
        res.contentLength = res.content.readableBytes
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

  val addLayout: Filter[Request, Renderer, Request, Renderer] =
    Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
      svc(req) map { renderer =>
        response: Response => {
          renderer(response)
          val r = MustacheRenderer("v2/layout.mustache", Map("body" -> response.contentString))
          r(response)
        }
      }
    }

  val requireServiceName =
    new SimpleFilter[Request, Renderer] {
      private[this] val Err = Future.value(ErrorRenderer(401, "Service name required"))
      def apply(req: Request, svc: Service[Request, Renderer]): Future[Renderer] =
        req.params.get("serviceName") match {
          case Some(_) => svc(req)
          case None => Err
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

  case class MustacheServiceCount(name: String, count: Int)
  case class MustacheTraceSummary(
    traceId: String,
    startTime: String,
    timestamp: Long,
    duration: Long,
    durationStr: String,
    spanCount: Int,
    serviceCounts: Seq[MustacheServiceCount],
    width: Int)
  case class MustacheTraceId(id: String)

  private[this] def traceSummaryToMustache(ts: Seq[TraceSummary]): Map[String, Any] = {
    val (maxDuration, minStartTime, maxStartTime) = ts.foldLeft((Long.MinValue, Long.MaxValue, Long.MinValue)) { case ((maxD, minST, maxST), t) =>
      ( math.max(t.durationMicro / 1000, maxD),
        math.min(t.startTimestamp, minST),
        math.max(t.startTimestamp, maxST)
      )
    }

    val traces = ts map { t =>
      val duration = t.durationMicro / 1000
      MustacheTraceSummary(
        SpanId(t.traceId).toString,
        QueryExtractor.fmt.format(new java.util.Date(t.startTimestamp / 1000)),
        t.startTimestamp,
        duration,
        durationStr(t.durationMicro.toLong * 1000),
        t.serviceCounts.foldLeft(0) { case (acc, (_, c)) => acc + c },
        t.serviceCounts.toSeq map { case (n, c) => MustacheServiceCount(n, c) },
        ((duration.toFloat / maxDuration) * 100).toInt
      )
    } sortBy(_.duration) reverse

    Map(
      ("traces" -> traces),
      ("count" -> traces.size))
  }

  def handleIndex(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val serviceName = req.params.get("serviceName")
      val spanName = req.params.get("spanName")

      val qr = QueryExtractor(req)
      val qResults = qr map { query(client, _, req) } getOrElse { EmptyTraces }
      val spanResults = serviceName map(client.getSpanNames(_).map(_.toSeq.sorted)) getOrElse(Future.value(Seq.empty))

      for (services <- getServices(client); results <- qResults; spans <- spanResults) yield {
        val svcList = services map { svc => Map("name" -> svc, "selected" -> (if (Some(svc) == serviceName) "selected" else "")) }
        val spanList = spans map { span => Map("name" -> span, "selected" -> (if (Some(span) == spanName) "selected" else "")) }

        var data = Map[String, Object](
          ("pageTitle" -> "Index"),
          ("timestamp" -> QueryExtractor.getTimestampStr(req)),
          ("annotationQuery" -> req.params.get("annotationQuery").getOrElse("")),
          ("services" -> svcList),
          ("spans" -> spanList),
          ("limit" -> req.params.get("limit").getOrElse("100")))

        qr foreach { qReq =>
          val binAnn = qReq.binaryAnnotations map { _.map { b =>
            (b.key, new String(b.value.array, b.value.position, b.value.remaining))
          } }
          data ++= Map(
            ("serviceName" -> qReq.serviceName),
            ("queryResults" -> traceSummaryToMustache(results)),
            ("annotations" -> qReq.annotations),
            ("binaryAnnotations" -> binAnn))
        }
        MustacheRenderer("v2/index.mustache", data)
      }
    }

  case class MustacheRealtimeSummary(
    clientServiceName: String,
    reqCount: Long,
    width: Int,
    p50: Long,
    p90: Long,
    p99: Long,
    p999: Long,
    traceIds: collection.Seq[MustacheTraceId])

  private[this] def getExistingTracedId(
    client: ZipkinQuery[Future],
    traceIds: collection.Map[String, Seq[Long]]): Future[Seq[(String, Seq[Long])]] = {
      Future.collect(traceIds.toList.map { case (svcName, traceIds) =>
        client.tracesExist(traceIds) map {(svcName -> _.toSeq)} })
  }

  private[this] def realtimeSummaryToMustache(
    client: ZipkinQuery[Future],
    durationMap: collection.Map[String, Seq[Long]],
    traceIds: Seq[(String, Seq[Long])]
  ): Map[String, Any] = {
    val maxReqs = durationMap.values.foldLeft(Int.MinValue) { case (maxR, t) =>
      math.max(t.size, maxR)
    }
    val traceIdsMap = traceIds.foldLeft(Option(Map[String, Seq[Long]]())) {
      case (Some(m),e @ (k,v)) if m.getOrElse(k, v) == v => Some(m + e)
      case _ => None
    }.getOrElse(Map.empty[String, Seq[Long]])
    val summary = durationMap map { e =>
      e match {
        case (serviceName, durations) =>
          val h = new ApproximateHistogram()
          durations.map(i => h.add(i / 1000L))
          MustacheRealtimeSummary(
            serviceName,
            durations.size,
            ((durations.size.toFloat / maxReqs) * 70).toInt + 30,
            h.getQuantile(0.5),
            h.getQuantile(0.9),
            h.getQuantile(0.99),
            h.getQuantile(0.999),
            traceIdsMap.get(serviceName)
              .getOrElse(Seq.empty[Long])
              .map(id => MustacheTraceId(SpanId(id).toString))
          )
      }
    }
    Map(
      ("summary" -> summary.toList.sortBy(_.reqCount).reverse),
      ("count" -> durationMap.size)
    )
  }

  def handleRealtime(client: ZipkinQuery[Future]): Service[Request, MustacheRenderer] =
    Service.mk[Request, MustacheRenderer] { req =>
      val qr = QueryExtractor(req)
      val spanDurationResults = qr map { querySpanDurantions(client, _) } getOrElse { EmptyRealtimeAgg }
      val svc2traceIdResults = qr map { queryServiceNamesToTraceIds(client, _) } getOrElse { EmptyRealtimeAgg }

      val serviceName = req.params.get("serviceName")
      val spanName = req.params.get("spanName")
      val spanResults = serviceName map(client.getSpanNames(_).map(_.toSeq.sorted)) getOrElse(Future.value(Seq.empty))

      for (
        services <- getServices(client);
        spans <- spanResults;
        spanDurantions <- spanDurationResults;
        svc2traceIds <- svc2traceIdResults;
        svc2existingTraceIds <- getExistingTracedId(client, svc2traceIds)
      ) yield {
        val svcList = services map { svc => Map("name" -> svc, "selected" -> (if (Some(svc) == serviceName) "selected" else "")) }
        val spanList = spans map { span => Map("name" -> span, "selected" -> (if (Some(span) == spanName) "selected" else "")) }

        var data = Map[String, Object](
          ("pageTitle" -> "Realtime"),
          ("timestamp" -> QueryExtractor.getTimestampStr(req)),
          ("services" -> svcList),
          ("spans" -> spanList))

        data ++= Map(
          ("pageTitle" -> "Realtime"),
          ("serviceName" -> req.params.get("serviceName")),
          ("realtimeResults" -> realtimeSummaryToMustache(client, spanDurantions, svc2existingTraceIds))
        )
        MustacheRenderer("v2/realtime.mustache", data)
      }
    }

  // API Endpoints

  def handleQuery(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val res = QueryExtractor(req) match {
        case Some(qr) => query(client, qr, req)
        case None => EmptyTraces
      }
      res map { JsonRenderer(_) }
    }

  def handleServices(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { _ =>
    getServices(client) map { JsonRenderer(_) }
  }

  def handleSpans(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getSpanNames(req.params("serviceName")) map { spans =>
        JsonRenderer(spans.toSeq.sorted)
      }
    }

  def handleTopAnnotations(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getTopAnnotations(req.params("serviceName")) map { ann => JsonRenderer(ann.toSeq.sorted) }
    }

  def handleTopKVAnnotations(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getTopKeyValueAnnotations(req.params("serviceName")) map { ann => JsonRenderer(ann.toSeq.sorted) }
    }

  def handleDependencies(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    new Service[Request, Renderer] {
      private[this] val PathMatch = """/api/dependencies(/([^/]+))?(/([^/]+))?/?""".r
      def apply(req: Request): Future[Renderer] = {
        val (startTime, endTime) = req.path match {
          case PathMatch(_, startTime, _, endTime) => (Option(startTime), Option(endTime))
          case _ => (None, None)
        }
        client.getDependencies(startTime.map(_.toLong), endTime.map(_.toLong)) map { JsonRenderer(_) }
      }
    }

  private[this] def pathTraceId(id: Option[String]): Option[Long] =
    id flatMap { SpanId.fromString(_).map(_.toLong) }

  trait NotFoundService extends Service[Request, Renderer] {
    def process(req: Request): Option[Future[Renderer]]

    def apply(req: Request): Future[Renderer] =
      process(req) getOrElse NotFound
  }

  private[this] def renderTrace(combo: TraceCombo): Renderer = {
    val trace = combo.trace.toTrace
    val traceStartTimestamp = trace.getStartAndEndTimestamp.map(_.start).getOrElse(0L)
    val childMap = trace.getIdToChildrenMap
    val spanMap = trace.getIdToSpanMap

    val spans = for {
      rootSpan <- trace.getRootSpans().sortBy(_.firstAnnotation.map(_.timestamp))
      span <- trace.getSpanTree(rootSpan, childMap).toList
    } yield {

      val start = span.firstAnnotation.map(_.timestamp).getOrElse(traceStartTimestamp)

      val depth = combo.spanDepths.get.getOrElse(span.id, 1)
      val width = span.duration.map { d => (d.toDouble / trace.duration.toDouble) * 100 }.getOrElse(0.0)
      Map(
        "spanId" -> SpanId(span.id).toString,
        "parentId" -> span.parentId.filter(spanMap.get(_).isDefined).map(SpanId(_).toString),
        "spanName" -> span.name,
        "serviceNames" -> span.serviceNames.mkString(","),
        "duration" -> span.duration,
        "durationStr" -> span.duration.map { d => durationStr(d * 1000) },
        "left" -> ((start - traceStartTimestamp).toFloat / trace.duration.toFloat) * 100,
        "width" -> (if (width < 0.1) 0.1 else width),
        "depth" -> (depth + 1) * 5,
        "depthClass" -> (depth - 1) % 6,
        "children" -> childMap.get(span.id).map(_.map(s => SpanId(s.id).toString).mkString(",")),
        "annotations" -> span.annotations.sortBy(_.timestamp).map { a =>
          Map(
            "isCore" -> CoreAnnotations.contains(a.value),
            "left" -> span.duration.map { d => ((a.timestamp - start).toFloat / d.toFloat) * 100 },
            "endpoint" -> a.host.map { e => "%s:%d".format(e.getHostAddress, e.getUnsignedPort) },
            "value" -> annoToString(a.value),
            "timestamp" -> a.timestamp,
            "relativeTime" -> durationStr((a.timestamp - traceStartTimestamp) * 1000),
            "serviceName" -> a.host.map(_.serviceName),
            "duration" -> a.duration,
            "width" -> a.duration.getOrElse(8)
          )
        },
        "binaryAnnotations" -> span.binaryAnnotations.map(JsonBinaryAnnotation.wrap)
      )
    }

    val traceDuration = trace.duration * 1000
    val serviceCounts = combo.summary.get.serviceCounts.toSeq map { case (n, c) => MustacheServiceCount(n, c) }
    val timeMarkers = Seq[Double](0.0, 0.2, 0.4, 0.6, 0.8, 1.0).zipWithIndex map { case (p, i) =>
      Map("index" -> i, "time" -> durationStr((traceDuration * p).toLong))
    }

    val data = Map[String, Object](
      "duration" -> durationStr(traceDuration),
      "services" -> combo.summary.map(_.serviceCounts.size),
      "depth" -> combo.spanDepths.map(_.values.max),
      "totalSpans" -> spans.size.asInstanceOf[Object],
      "serviceCounts" -> serviceCounts.sortBy(_.name),
      "timeMarkers" -> timeMarkers,
      "spans" -> spans)

    MustacheRenderer("v2/trace.mustache", data)
  }

  def handleTraces(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      pathTraceId(req.path.split("/").lastOption) map { id =>
        client.getTraceCombosByIds(Seq(id), getAdjusters(req)) flatMap {
          case Seq(combo) => Future.value(renderTrace(combo))
          case _ => NotFound
        }
      } getOrElse NotFound
    }

  def handleGetTrace(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    new NotFoundService {
      def process(req: Request): Option[Future[Renderer]] =
        pathTraceId(req.path.split("/").lastOption) map { id =>
          client.getTraceCombosByIds(Seq(id), getAdjusters(req)) map { ts =>
            val combo = ts.head.toTraceCombo
            JsonRenderer(if (req.path.startsWith("/api/trace")) combo.trace else combo)
          }
        }
    }

  def handleIsPinned(client: ZipkinQuery[Future]): Service[Request, Renderer] =
    new NotFoundService {
      def process(req: Request): Option[Future[Renderer]] =
        pathTraceId(req.path.split("/").lastOption) map { id =>
          client.getTraceTimeToLive(id) map { ttl => JsonRenderer(ttl) }
        }
    }

  def handleTogglePin(client: ZipkinQuery[Future], pinTtl: Duration): Service[Request, Renderer] =
    new NotFoundService {
      private[this] val Err = Future.value(ErrorRenderer(400, "Must be true or false"))
      private[this] val SetState = Future.value(pinTtl.inSeconds)
      private[this] def togglePinState(traceId: Long, state: Boolean): Future[Boolean] = {
        val ttl = if (state) SetState else client.getDataTimeToLive()
        ttl flatMap { client.setTraceTimeToLive(traceId, _) } map { _ => state }
      }

      def process(req: Request): Option[Future[Renderer]] =
        req.path.split("/") match {
          case Array("", "api", "pin", traceId, stateVal) =>
            pathTraceId(Some(traceId)) map { id =>
              val state = stateVal match {
                case "true" => Some(true)
                case "false" => Some(false)
                case _ => None
              }
              state map { s =>
                togglePinState(id, s) map { v => JsonRenderer(v) }
              } getOrElse {
                Err
              }
            }
          case _ => None
        }
    }
}
