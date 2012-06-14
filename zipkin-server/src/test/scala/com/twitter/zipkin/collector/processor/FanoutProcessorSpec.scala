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
package com.twitter.zipkin.collector.processor

import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}

class FanoutProcessorSpec extends Specification with JMocker with ClassMocker {
  "FanoutProcessor" should {
    "fanout" in {
      val proc1 = mock[Processor[Int]]
      val proc2 = mock[Processor[Int]]

      val fanout = new FanoutProcessor[Int](Seq(proc1, proc2))
      val item = 1

      expect {
        one(proc1).process(item)
        one(proc2).process(item)
      }

      fanout.process(item)
    }
  }
}
