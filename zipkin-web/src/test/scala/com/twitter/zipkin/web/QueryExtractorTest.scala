/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.web

import com.twitter.finagle.httpx.Request
import com.twitter.util.Time
import org.scalatest.FunSuite

class QueryExtractorTest extends FunSuite {

  val queryExtractor = new QueryExtractor(10)

  def request(p: (String, String)*) = Request(p: _*)

  test("getTimestampStr") {
    val endTs = Time.now
    val endTimestamp = endTs.inMicroseconds.toString
    val r = request(
      "serviceName" -> "myService",
      "spanName" -> "mySpan",
      "timestamp" -> endTimestamp,
      "limit" -> "1000")

    val actual = queryExtractor.getTimestampStr(r)
    assert(actual === endTs.inMicroseconds.toString)
  }

  test("default getTimestampStr") {
    Time.withCurrentTimeFrozen { tc =>
      val actual = queryExtractor.getTimestampStr(request("serviceName" -> "myService"))
      assert(actual === Time.now.sinceEpoch.inMicroseconds.toString)
    }
  }

  test("parse limit") {
    val r = request("serviceName" -> "myService", "limit" -> "199")
    val actual = queryExtractor.getLimitStr(r)
    assert(actual === 199.toString)
  }

  test("default limit") {
    val actual = new QueryExtractor(100).getLimitStr(request("serviceName" -> "myService"))
    assert(actual === 100.toString)
  }

  test("parse annotations") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "finagle.retry and finagle.timeout")
    val actual = queryExtractor.getAnnotations(r).get
    assert(actual._1.contains("finagle.retry"))
    assert(actual._1.contains("finagle.timeout"))
  }

  test("parse key value annotations") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "http.responsecode=500")
    val actual = queryExtractor.getAnnotations(r).get
    assert(actual._2 === Map("http.responsecode" -> "500"))
  }

  test("parse key value annotations with slash") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "http.uri=/sessions")
    val actual = queryExtractor.getAnnotations(r).get
    assert(actual._2 === Map("http.uri" -> "/sessions"))
  }
}
