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

  test("endTs must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", endTs = 0)
    }
  }

  test("limit must be positive") {
    intercept[IllegalArgumentException] {
      QueryRequest("foo", limit = 0)
    }
  }
}
