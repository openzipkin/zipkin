package com.twitter.zipkin.web

import com.twitter.finagle.httpx.{Response, Request}
import com.twitter.finagle.tracing.{DefaultTracer, Trace}
import com.twitter.finagle.{Filter, ServiceFactory, param, Stack}
import com.twitter.util.Future

/**
 * Hacked variant of the private `com.twitter.finagle.httpx.codec.HttpServerTraceInitializer`
 *
 * <p/>This version only starts traces at certain entrypoints.
 */
object FilteredHttpEntrypointTraceInitializer extends Stack.Module1[param.Tracer, ServiceFactory[Request, Response]] {
  val role = Stack.Role("TraceInitializerFilter")
  val description = "Initialize the tracing system with trace info from the incoming request"

  override def make(ignored: param.Tracer, next: ServiceFactory[Request, Response]) = {
    val traceInitializer = Filter.mk[Request, Response, Request, Response] { (req, svc) =>
      if (req.path.equals("/") || req.path.equals("/dependency") || req.path.startsWith("/api/")) {
        newRootSpan(req, svc)
      } else {
        svc(req)
      }
    }
    traceInitializer andThen next
  }

  /** Same behavior as `com.twitter.finagle.httpx.codec.HttpServerTraceInitializer` */
  private def newRootSpan(req: Request, svc: (Request) => Future[Response]) = {
    Trace.letTracerAndId(DefaultTracer.self, Trace.nextId) {
      Trace.recordRpc(req.method.toString())
      val withoutQuery = req.uri.indexOf('?') match {
        case -1 => req.uri
        case n => req.uri.substring(0, n)
      }
      Trace.recordBinary("http.uri", withoutQuery)
      svc(req)
    }
  }
}
