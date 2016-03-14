package com.twitter.zipkin.web

import com.google.common.io.ByteStreams
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.{Stat, StatsReceiver}
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Filter, Service}
import com.twitter.finatra.httpclient.HttpClient
import com.twitter.io.Buf
import com.twitter.util.Future
import com.twitter.zipkin.json._
import com.twitter.conversions.time._
import org.jboss.netty.handler.codec.http.QueryStringEncoder
import java.io.{File, InputStream}

class Handlers {

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

  def handlePublic(
    resourceDirs: Set[String],
    typesMap: Map[String, String]) =
    new Service[Request, Renderer] {
      private[this] var rendererCache = Map.empty[String, Future[Renderer]]

      private[this] def getStream(path: String): Option[InputStream] = {
        val resource = getClass.getResource(path)
        if (resource == null || new File(resource.getPath).isDirectory) {
          None
        } else {
          Option(resource.openStream())
        }
      }

      private[this] def getRenderer(path: String): Option[Future[Renderer]] = {
        rendererCache.get(path) orElse {
          synchronized {
            rendererCache.get(path) orElse {
              resourceDirs find(path.startsWith) flatMap { _ =>
                getStream(path) map { input =>
                  val typ = typesMap find { case (n, _) => path.endsWith(n) } map { _._2 } getOrElse("text/plain")
                  val renderer = Future.value(StaticRenderer(input, typ))
                  rendererCache += (path -> renderer)
                  renderer
                } orElse Option(Future(StaticRenderer(getClass.getResourceAsStream("/index.html"), "text/html")))
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

  def handleRoute(client: HttpClient, baseUri: String): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      val encoder = new QueryStringEncoder(baseUri)
      req.params.foreach { case (key, value) =>
        encoder.addParam(key, value)
      }
      client.execute(Request(encoder.toString)).map(CopyRenderer)
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

  def handleTrace(client: HttpClient): Service[Request, Renderer] =
    Service.mk[Request, Renderer] { req =>
      pathTraceId(req.path.split("/").lastOption) map { id =>
        client.execute(Request(s"/api/v1/trace/$id"))
          .map(CopyRenderer)
      } getOrElse NotFound
    }
}
