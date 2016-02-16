package com.twitter.zipkin.util

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.twitter.util.Future
import com.twitter.util.Promise

object FutureUtil {
  def toFuture[T](listenable: ListenableFuture[T]): Future[T] = {
    val promise = Promise[T]()

    Futures.addCallback(listenable, new FutureCallback[T]() {
      override def onFailure(t: Throwable): Unit = promise.setException(t)

      override def onSuccess(result: T): Unit = promise.setValue(result)
    })

    promise
  }
}
