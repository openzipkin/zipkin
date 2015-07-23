package com.twitter.zipkin.storage.hbase.utils

import java.util.concurrent.atomic.AtomicLong

import com.twitter.zipkin.storage.util.Retry
import org.scalatest.{FunSuite, Matchers}

class RetryTest extends FunSuite with Matchers {
  test("return if success") {
    val counter = new AtomicLong(0)
    val result = Retry(10) {
      val innerResult: Long = counter.incrementAndGet()
      LongWrapper(innerResult)
    }
    result should be(LongWrapper(1L))
  }

  test("throw an error if retries are exhausted") {
    intercept[Retry.RetriesExhaustedException]{
      val result = Retry(5) {
        throw new Exception("No! No! No!")
        LongWrapper(1)
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
      LongWrapper(innerResult)
    }
    result should be (LongWrapper(10L))
  }
  case class LongWrapper(value:Long)
}
