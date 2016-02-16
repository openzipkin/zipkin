package com.twitter.zipkin.common

import com.twitter.zipkin.storage.QueryRequest
import org.scalatest.FunSuite

class QueryRequestTest extends FunSuite {

  test("serviceName can't be empty") {
    intercept[IllegalArgumentException] {
      QueryRequest("")
    }
  }

  test("spanName can't be empty") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", Some(""))
    }
  }

  test("minDuration must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", minDuration = Some(0))
    }
  }

  test("minDuration is required when specifying maxDuration") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", maxDuration = Some(34))
    }
  }

  test("maxDuration must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", minDuration = Some(1), maxDuration = Some(0))
    }
  }

  test("endTs must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", endTs = 0)
    }
  }

  test("lookback must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", lookback = Some(0))
    }
  }

  test("limit must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", limit = 0)
    }
  }
}
