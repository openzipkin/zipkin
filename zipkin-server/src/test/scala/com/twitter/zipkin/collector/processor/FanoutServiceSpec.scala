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

import com.twitter.finagle.Service
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}

class FanoutServiceSpec extends Specification with JMocker with ClassMocker {
  "FanoutService" should {
    "fanout" in {
      val serv1 = mock[Service[Int, Unit]]
      val serv2 = mock[Service[Int, Unit]]

      val fanout = new FanoutService[Int](Seq(serv1, serv2))
      val item = 1

      expect {
        one(serv1).apply(item)
        one(serv2).apply(item)
      }

      fanout.apply(item)
    }
  }
}
