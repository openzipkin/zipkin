package com.twitter.zipkin.web

import com.twitter.conversions.time._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Filter, Service, SimpleFilter}
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.json.ZipkinJson
import com.twitter.zipkin.common.mustache.ZipkinMustache
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.gen.{ZipkinQuery, Adjust}
import com.twitter.zipkin.query.{TraceSummary, QueryRequest}
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import org.apache.commons.io.IOUtils
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

trait Renderer {
  def render(response: Response)

  def response: Response = {
    val response = Response()
    render(response)
    response.contentLength = response.content.readableBytes
    response
  }
}

case class ErrorRenderer(code: Int, msg: String) extends Renderer {
  def render(response: Response) {
    MustacheRenderer("templates/layouts/application.mustache", Map(
      ("body" -> MustacheRenderer("templates/error.mustache", Map(("errorMsg" -> msg))).generate))
    ).render(response)
    response.statusCode = code
  }
}

case class MustacheRenderer(template: String, data: Map[String, Object]) extends Renderer {
  def render(response: Response) {
    response.contentString = generate
  }

  def generate = ZipkinMustache.render(template, data)
}

case class JsonRenderer(data: Any) extends Renderer {
  def render(response: Response) {
    response.setContentTypeJson()
    response.contentString = ZipkinJson.generate(data)
  }
}

case class StaticRenderer(input: InputStream, typ: String) extends Renderer {
  override val response = Response()
  response.setContentType(typ)
  response.content = {
    val bytes = IOUtils.toByteArray(input)
    input.read(bytes)
    ChannelBuffers.wrappedBuffer(bytes)
  }
  response.contentLength = response.content.readableBytes

  def render(response: Response) {}
}

object Handlers {
  private[this] val EmptyTraces = Future.value(Seq.empty[TraceSummary])
  private[this] val NotFound = Future.value(ErrorRenderer(404, "Not Found"))

  private[this] val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  private[this] val timeFormat = new SimpleDateFormat("HH:mm:ss")
  private[this] def getDate = dateFormat.format(Calendar.getInstance().getTime)
  private[this] def getTime = timeFormat.format(Calendar.getInstance().getTime)

  private[this] def query(
    client: ZipkinQuery.FutureIface,
    queryRequest: QueryRequest,
    request: Request,
    retryLimit: Int = 10
  ): Future[Seq[TraceSummary]] = {
    /* Get trace ids */
    val response = client.getTraceIds(queryRequest.toThrift) map { _.toQueryResponse }

    response flatMap { resp =>
      resp.traceIds match {
        case Nil =>
          /* Complex query, so retry */
          if (retryLimit > 0 && queryRequest.hasAnnotations)
            query(client, queryRequest.copy(endTs = resp.endTs), request, retryLimit - 1)
          else
            EmptyTraces

        case ids =>
          val adjusters = getAdjusters(request)
          client.getTraceSummariesByIds(ids, adjusters) map { _.map { _.toTraceSummary } }
      }
    }
  }

  private[this] def getServices(client: ZipkinQuery.FutureIface): Future[Seq[TracedService]] =
    client.getServiceNames() map { _.toSeq.sorted map { TracedService(_) } }

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
      svc(req) map { _.response }
    }

  def checkPath(path: List[String]): Filter[Request, Renderer, Request, Renderer] = {
    val requiredSize = path.takeWhile { !_.startsWith(":") }.size
    if (path.size == requiredSize) Filter.identity[Request, Renderer] else
      Filter.mk[Request, Renderer, Request, Renderer] { (req, svc) =>
        if (req.path.split("/").size >= path.size) svc(req) else NotFound
      }
  }


  def addLayout(rootUrl: String): Filter[Request, Renderer, Request, MustacheRenderer] =
    Filter.mk[Request, Renderer, Request, MustacheRenderer] { (req, svc) =>
      svc(req) map { r =>
        MustacheRenderer("templates/layouts/application.mustache", r.data ++ Map(
          ("body" -> r.generate),
          ("rootUrl" -> rootUrl)))
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

  def handlePublic(resourceDirs: Map[String, String]) =
    new Service[Request, Renderer] {
      private[this] var rendererCache = Map.empty[String, Future[Renderer]]

      private[this] def getRenderer(path: String): Option[Future[Renderer]] = {
        rendererCache.get(path) orElse {
          synchronized {
            rendererCache.get(path) orElse {
              resourceDirs find { case (k, _) => path.startsWith(k) } flatMap { case (_, typ) =>
                Option(getClass.getResourceAsStream(path)) filter { _.available > 0 } map { input =>
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

  def handleIndex(client: ZipkinQuery.FutureIface): Service[Request, MustacheRenderer] =
    Service.mk[Request, MustacheRenderer] { req =>
      val qr = QueryExtractor(req)
      val qResults = qr map { query(client, _, req) } getOrElse { EmptyTraces }
      for (services <- getServices(client); results <- qResults) yield {
        var data = Map[String, Object](
          ("pageTitle" -> "Index"),
          ("endDate" -> getDate),
          ("endTime" -> getTime),
          ("jsonServices" -> ZipkinJson.generate(services)),
          ("jsonQueryResults" -> ZipkinJson.generate(qResults)))

        qr foreach { qReq =>
          val binAnn = qReq.binaryAnnotations map { _.map { b =>
            (b.key, new String(b.value.array, b.value.position, b.value.remaining))
          } }
          data ++= Map(
            ("pageTitle" -> qReq.serviceName),
            ("annotations" -> qReq.annotations),
            ("binaryAnnotations" -> binAnn))
        }
        MustacheRenderer("templates/index.mustache", data)
      }
    }

  val handleStatic =
    new Service[Request, MustacheRenderer] {
      private[this] val StaticRenderer =
        Future.value(MustacheRenderer("templates/static.mustache", Map(
          ("pageTitle" -> "Static"))))

      def apply(req: Request): Future[MustacheRenderer] = StaticRenderer
    }

  val handleAggregates =
    Service.mk[Request, MustacheRenderer] { _ =>
      Future.value(MustacheRenderer("templates/aggregates.mustache", Map(
        ("pageTitle" -> "Aggregates"),
        ("endDate" -> getDate))))
    }

  val handleTraces =
    Service.mk[Request, MustacheRenderer] { req =>
      val id = req.path.split("/").last
      Future.value(MustacheRenderer("templates/show.mustache", Map(
        ("pageTitle" -> "Trace %s".format(id)),
        ("traceId" -> id))))
    }

  // API Endpoints

  def handleQuery(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val res = QueryExtractor(req) match {
        case Some(qr) => query(client, qr, req)
        case None => EmptyTraces
      }
      res map { JsonRenderer(_) }
    }

  def handleServices(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { _ =>
    getServices(client) map { JsonRenderer(_) }
  }

  def handleSpans(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getSpanNames(req.params("serviceName")) map { spans =>
        JsonRenderer(spans.toSeq.sorted map { s => Map("name" -> s) })
      }
    }

  def handleTopAnnotations(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getTopAnnotations(req.params("serviceName")) map { ann => JsonRenderer(ann.toSeq.sorted) }
    }

  def handleTopKVAnnotations(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      client.getTopKeyValueAnnotations(req.params("serviceName")) map { ann => JsonRenderer(ann.toSeq.sorted) }
    }

  def handleDependencies(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
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

  def handleGetTrace(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    new NotFoundService {
      def process(req: Request): Option[Future[Renderer]] =
        pathTraceId(req.path.split("/").lastOption) map { id =>
          client.getTraceCombosByIds(Seq(id), getAdjusters(req)) map { ts =>
            val combo = ts.head.toTraceCombo
            JsonRenderer(if (req.path.startsWith("/api/trace")) combo.trace else combo)
          }
        }
    }

  def handleIsPinned(client: ZipkinQuery.FutureIface): Service[Request, Renderer] =
    new NotFoundService {
      def process(req: Request): Option[Future[Renderer]] =
        pathTraceId(req.path.split("/").lastOption) map { id =>
          client.getTraceTimeToLive(id) map { ttl => JsonRenderer(ttl) }
        }
    }

  def handleTogglePin(client: ZipkinQuery.FutureIface, pinTtl: Duration): Service[Request, Renderer] =
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
