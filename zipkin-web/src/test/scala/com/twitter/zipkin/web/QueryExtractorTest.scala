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

import java.nio.ByteBuffer

import com.twitter.finagle.http.Request
import com.twitter.util.Time
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import org.scalatest.FunSuite

class QueryExtractorTest extends FunSuite {

  val queryExtractor = new QueryExtractor(10)

  def request(p: (String, String)*) = Request(p:_*)

  test("require serviceName") {
    assert(!queryExtractor(request()).isDefined)
  }

  test("parse params") {
    val endTs = Time.now
    val endTimestamp = endTs.inMicroseconds.toString
    val r = request(
      "serviceName" -> "myService",
      "spanName" -> "mySpan",
      "timestamp" -> endTimestamp,
      "limit" -> "1000")

    val actual = queryExtractor(r).get

    assert(actual.serviceName === "myService")
    assert(actual.spanName.get === "mySpan")
    assert(actual.endTs ===  endTs.inMicroseconds)
    assert(actual.limit === 1000)
  }

  test("default endDateTime") {
    Time.withCurrentTimeFrozen { tc =>
      val actual = queryExtractor(request("serviceName" -> "myService")).get
      assert(actual.endTs === Time.now.sinceEpoch.inMicroseconds)
    }
  }

  test("parse limit") {
    val r = request("serviceName" -> "myService", "limit" -> "199")
    val actual = queryExtractor(r).get
    assert(actual.limit === 199)
  }

  test("default limit") {
    val actual = new QueryExtractor(100).apply(request("serviceName" -> "myService")).get
    assert(actual.limit === 100)
  }

  test("parse spanName 'all'") {
    val r = request("serviceName" -> "myService", "spanName" -> "all")
    val actual = queryExtractor(r).get
    assert(!actual.spanName.isDefined)
  }

  test("parse spanName ''") {
    val r = request("serviceName" -> "myService", "spanName" -> "")
    val actual = queryExtractor(r).get
    assert(!actual.spanName.isDefined)
  }

  test("parse spanName") {
    val r = request("serviceName" -> "myService", "spanName" -> "something")
    val actual = queryExtractor(r).get
    assert(actual.spanName.get === "something")
  }

  test("parse annotations") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "finagle.retry and finagle.timeout")
    val actual = queryExtractor(r).get
    assert(actual.annotations.get.contains("finagle.retry"))
    assert(actual.annotations.get.contains("finagle.timeout"))
  }

  test("parse key value annotations") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "http.responsecode=500")
    val actual = queryExtractor(r).get
    assert(
      actual.binaryAnnotations.get ===
        Seq(BinaryAnnotation("http.responsecode", ByteBuffer.wrap("500".getBytes), AnnotationType.String, None)))
  }

  test("parse key value annotations with slash") {
    val r = request(
      "serviceName" -> "myService",
      "annotationQuery" -> "http.uri=/sessions")
    val actual = queryExtractor(r).get
    assert(
      actual.binaryAnnotations.get ===
        Seq(BinaryAnnotation("http.uri", ByteBuffer.wrap("/sessions".getBytes), AnnotationType.String, None)))
  }
}
