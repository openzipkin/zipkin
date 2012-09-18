package com.twitter.zipkin.collector.processor

import com.twitter.finagle.Service
import com.twitter.util.Future

class NullService[T] extends Service[T, Unit] {
  def apply(t: T): Future[Unit] = Future.Unit
}
