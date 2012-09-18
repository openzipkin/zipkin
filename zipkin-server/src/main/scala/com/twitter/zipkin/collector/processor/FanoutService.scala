package com.twitter.zipkin.collector.processor

import com.twitter.finagle.Service
import com.twitter.util.Future

class FanoutService[-Req](services: Seq[Service[Req, Unit]]) extends Service[Req, Unit] {
  def apply(req: Req): Future[Unit] = {
    Future.join {
      services map { _.apply(req) }
    }
  }

  override def release() {
    services foreach { _.release() }
  }
}
