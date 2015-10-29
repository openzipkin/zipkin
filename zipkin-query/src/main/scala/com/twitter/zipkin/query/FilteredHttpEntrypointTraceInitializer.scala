package com.twitter.zipkin.query

import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.finagle.tracing.{DefaultTracer, Trace}
import com.twitter.finagle.{Filter, ServiceFactory, Stack, param}
import com.twitter.util.Future

/**
 * Hacked variant of the private `com.twitter.finagle.http.codec.HttpServerTraceInitializer`
 *
 * <p/>This version doesn't trace POST requests
 *
 * <p/>See https://github.com/twitter/finatra/issues/271
 */
object FilteredHttpEntrypointTraceInitializer extends Stack.Module1[param.Tracer, ServiceFactory[Request, Response]] {
  val role = Stack.Role("TraceInitializerFilter")
  val description = "Initialize the tracing system with trace info from the incoming request"

  override def make(ignored: param.Tracer, next: ServiceFactory[Request, Response]) = {
    val traceInitializer = Filter.mk[Request, Response, Request, Response] { (req, svc) =>
      if (req.method != Method.Post) {
        newRootSpan(req, svc)
      } else {
        svc(req)
      }
    }
    traceInitializer andThen next
  }

  /** Same behavior as `com.twitter.finagle.http.codec.HttpServerTraceInitializer` */
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
