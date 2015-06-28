package com.twitter.zipkin.storage.hbase.utils

import org.junit.runner.RunWith
import org.scalatest.{WordSpec, MustMatchers}
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.atomic.AtomicLong
import com.twitter.zipkin.storage.util.Retry

@RunWith(classOf[JUnitRunner])
class RetryTest extends WordSpec with MustMatchers {
  "Retry" should {
    "return if success" in {
      val counter = new AtomicLong(0)
      val result = Retry(10) {
        val innerResult: Long = counter.incrementAndGet()
        LongWrapper(innerResult)
      }
      result mustEqual LongWrapper(1L)
    }
    "throw an error if retries are exhausted" in {
      intercept[Retry.RetriesExhaustedException]{
        val result = Retry(5) {
          throw new Exception("No! No! No!")
          LongWrapper(1)
        }
      }

    }
    "return if fewer than max retries are needed" in {
      val counter = new AtomicLong(0)
      val result = Retry(10) {
        val innerResult: Long = counter.incrementAndGet()
        if (innerResult < 10) {
           throw new Exception("No No No")
        }
        LongWrapper(innerResult)
      }
      result mustEqual LongWrapper(10L)
    }
  }
  case class LongWrapper(value:Long)
}
