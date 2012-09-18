package com.twitter.zipkin.collector.processor

import com.twitter.finagle.{Service, Filter}
import com.twitter.util.Future

class SequenceFilter[T] extends Filter[Seq[T], Unit, T, Unit] {
  def apply(req: Seq[T], service: Service[T, Unit]): Future[Unit] = {
    Future.join {
      req map { service(_) }
    }
  }
}
