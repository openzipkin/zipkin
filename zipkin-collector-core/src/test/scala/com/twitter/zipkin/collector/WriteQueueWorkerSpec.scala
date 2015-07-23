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

import java.util.concurrent.BlockingQueue

import com.twitter.finagle.Service
import com.twitter.util.Future
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class WriteQueueWorkerSpec extends FunSuite with Matchers with MockitoSugar {

  test("hand off to processor") {
    val service = mock[Service[Span, Unit]]
    val queue = mock[BlockingQueue[Span]]

    val w = new WriteQueueWorker[Span](queue, service)
    val span = Span(123, "boo", 456, None, List(Annotation(123, "value", Some(Endpoint(1,2,"service")))), Nil)

    when(service.apply(span)) thenReturn Future.Done

    w.process(span)

    verify(service).apply(span)
  }
}
