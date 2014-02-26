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
import com.twitter.zipkin.common.{Endpoint, Annotation, Span}

class SerializationTest extends FunSuite {
  val annotation1 = Annotation(1, "cs", Some(Endpoint(1, 2, "service")))
  val annotation2 = Annotation(2, "cr", Some(Endpoint(3, 4, "Service")))
  val annotation3 = Annotation(3, "cr", Some(Endpoint(5, 6, "Service")))
  val span = Span(12345, "methodcall", 666, None,
    List(annotation1, annotation2), Nil)
  val span2 = Span(6789, "methodcall2", 000, None,
    List(annotation3), Nil)
  //val spanScheme = new Serialization()

  test("spanMonoid plus") {
    val expectedSpan = Span(12345, "methodcall", 666, None,
      List(annotation1, annotation2), Nil)
    val spanAfterPlus = Serialization.spanMonoid.plus(span, span2)
    assert(expectedSpan === spanAfterPlus)
  }
}