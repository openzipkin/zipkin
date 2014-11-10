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
package com.twitter.zipkin.common

import com.twitter.conversions.time._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AnnotationTest extends FunSuite {
  test("get min of two annotations") {
    val ann1 = Annotation(1, "one", None)
    val ann2 = Annotation(2, "two", None)
    val annList = List(ann1, ann2)

    assert(annList.min === ann1)
  }

  test("compare correctly") {
    val ann1 = Annotation(1, "a", None, None)
    val ann2 = Annotation(2, "a", None)
    val ann3 = Annotation(1, "b", None)
    val ann4 = Annotation(1, "a", Some(Endpoint(1, 2, "service")))
    val ann5 = Annotation(1, "a", None, Some(1.second))

    assert(ann1.compare(ann1) === 0)
    assert(ann1.compare(ann2) < 0)
    assert(ann1.compare(ann3) < 0)
    assert(ann1.compare(ann4) < 0)
    assert(ann1.compare(ann5) < 0)
  }
}
