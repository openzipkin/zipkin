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

import org.specs.Specification
import com.twitter.conversions.time._

class AnnotationSpec extends Specification {
  "Annotation" should {

    "get min of two annotations" in {
      val ann1 = Annotation(1, "one", None)
      val ann2 = Annotation(2, "two", None)
      val annList = List(ann1, ann2)

      annList.min mustEqual ann1
    }

    "compare correctly" in {
      val ann1 = Annotation(1, "a", None, None)
      val ann2 = Annotation(2, "a", None)
      val ann3 = Annotation(1, "b", None)
      val ann4 = Annotation(1, "a", Some(Endpoint(1, 2, "service")))
      val ann5 = Annotation(1, "a", None, Some(1.second))

      ann1.compare(ann1) mustEqual 0
      ann1.compare(ann2) must beLessThan(0)
      ann1.compare(ann3) must beLessThan(0)
      ann1.compare(ann4) must beLessThan(0)
      ann1.compare(ann5) must beLessThan(0)
    }
  }
}
