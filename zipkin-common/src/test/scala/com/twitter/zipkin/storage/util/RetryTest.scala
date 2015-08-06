package com.twitter.zipkin.storage.util

import org.scalatest.{FunSuite, Matchers}
import java.util.concurrent.atomic.AtomicLong
import com.twitter.zipkin.storage.util

class RetryTest extends FunSuite with Matchers {
  test("retry if success") {
    val counter = new AtomicLong(0)
    val result = Retry(10) {
      val innerResult: Long = counter.incrementAndGet()
      innerResult
    }
    result should be (1)
  }
  test("throw an error if retries are exhausted") {
    a [Retry.RetriesExhaustedException] should be thrownBy {
      val result = Retry(5) {
        throw new Exception("No! No! No!")
        1
      }
    }
  }
  test("return if fewer than max retries are needed") {
    val counter = new AtomicLong(0)
    val result = Retry(10) {
      val innerResult: Long = counter.incrementAndGet()
      if (innerResult < 10) {
         throw new Exception("No No No")
      }
      innerResult
    }
    result should be (10)
  }
  test("sleeps when exception thrown and flag set") {
    val t1 = System.currentTimeMillis
    try {
      val result = Retry(2, true) {
        throw new Exception
      }
    }
    catch {
      case e: Throwable => null
    }
    val t2 = System.currentTimeMillis
    val delta = t2 - t1
    delta.toInt should be >= (2000)
  }
}
