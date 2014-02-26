/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.storm

import org.scalatest._
import com.twitter.zipkin.gen.{Annotation, Endpoint, Span}
import scala.collection.JavaConversions._

class SpanSchemeSpec extends FunSuite {
  val annotation1 = Annotation(1, "cs", Some(Endpoint(1, 2, "service")))
  val annotation2 = Annotation(2, "cr", Some(Endpoint(3, 4, "Service")))
  val span = Span(12345, "methodcall", 666, None,
    List(annotation1, annotation2), Nil)

  val spanScheme = new SpanScheme()
  val bytes = spanScheme.deserializer.toBytes(span)

  test("SpanScheme deserializes bytes to span" ) {
    val spanRecovered = spanScheme.deserializer.fromBytes(bytes)
    assert(spanRecovered === span)
  }

  test("SpanScheme return correct values of the fields") {
    val expectedValues = Seq(12345, 666, "methodcall", "service", true)
    val values = spanScheme.deserialize(bytes).toList
    assert(expectedValues === values)
  }
}