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
package com.twitter.zipkin.collector

import com.twitter.zipkin.collector.processor.Processor
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import java.util.concurrent.BlockingQueue

class WriteQueueWorkerSpec extends Specification with JMocker with ClassMocker {
  "WriteQueueWorker" should {
    "hand off to processor" in {
      val processor = mock[Processor[Span]]
      val queue = mock[BlockingQueue[Span]]

      val w = new WriteQueueWorker[Span](queue, processor)
      val span = Span(123, "boo", 456, None, List(Annotation(123, "value", Some(Endpoint(1,2,"service")))), Nil)

      expect {
        one(processor).process(span)
      }
      w.process(span)
    }
  }
}
