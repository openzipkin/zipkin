package com.twitter.zipkin.web

import com.twitter.finatra_core.AbstractFinatraController
import com.twitter.finatra.{Response, Request}
import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.util.Future

class ZipkinController
  extends AbstractFinatraController[Request, Future[HttpResponse], Future[HttpResponse]] {

  def render = new Response
}
