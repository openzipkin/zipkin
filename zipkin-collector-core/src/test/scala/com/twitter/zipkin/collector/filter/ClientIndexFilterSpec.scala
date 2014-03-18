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
package com.twitter.zipkin.collector.filter

import com.twitter.zipkin.common.{Endpoint, Annotation, Span}
import com.twitter.zipkin.gen
import org.specs.Specification

class ClientIndexFilterSpec extends Specification {

  val filter = new ClientIndexFilter

  "ClientIndexFilter" should {
    "not index span" in {
      // server side, with default name
      val spanCs = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.CLIENT_SEND, Some(Endpoint(1,1,"client")))), Nil)
      filter.shouldIndex(spanCs) mustEqual false
      val spanCr = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.CLIENT_RECV, Some(Endpoint(1,1,"client")))), Nil)
      filter.shouldIndex(spanCr) mustEqual false
    }

    "index span" in {
      // server side, so index
      val spanSr = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.SERVER_RECV, Some(Endpoint(1,1,"s")))), Nil)
      filter.shouldIndex(spanSr) mustEqual true
      val spanSs = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.SERVER_SEND, Some(Endpoint(1,1,"s")))), Nil)
      filter.shouldIndex(spanSs) mustEqual true
      // client side, but not with default name
      val spanCs = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.CLIENT_SEND, Some(Endpoint(1,1,"s")))), Nil)
      filter.shouldIndex(spanCs) mustEqual true
      val spanCr = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.CLIENT_RECV, Some(Endpoint(1,1,"s")))), Nil)
      filter.shouldIndex(spanCr) mustEqual true
      // the unusual case of having a server with the name "client"
      val spanClientServer = Span(1, "n", 2, None, List(Annotation(1, gen.Constants.SERVER_SEND, Some(Endpoint(1,1,"client")))), Nil)
      filter.shouldIndex(spanClientServer) mustEqual true
    }
  }
}
