package com.twitter.zipkin.storage.util

/**
 * Retry a given function up to nTries.
 *
 * All exceptions will be caught until the retry limit is met.
 *
 * No clean up between each invocation is attempted.  It's up to the user to ensue that
 * supplied function is resilient to this fact.
 */
object Retry {
  def apply[T](n: Int, sleep: Boolean = false, sleeptime:Int = 1000)(f: => T) : T = {
    var result:Option[T] = None
    var throwable:Option[Throwable] = None

    for (i <- 0 until n if result.isEmpty) {
      try {
        result = Option(f)
      } catch {
        case e: Throwable => {
            if (sleep) Thread.sleep(sleeptime)
            throwable = Some(e)
          }
      }
    }

    if (result.isEmpty && throwable.isDefined) {
      throw new RetriesExhaustedException(s"$n retries exhausted", throwable.get)
    }

    result.get
  }
  class RetriesExhaustedException(msg:String, throwable:Throwable) extends RuntimeException(msg,throwable)
}
