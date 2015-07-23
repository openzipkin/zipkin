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
package com.twitter.zipkin.sampler

import com.twitter.finagle.http.{HttpMuxer, RequestBuilder, Response}
import com.twitter.util.Await
import org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer
import org.scalatest.FunSuite

class HttpVarTest extends FunSuite {
  test("can request the current value") {
    val httpVar = new HttpVar("test1", 1.0)
    val req = RequestBuilder().url("http://localhost/vars/test1").buildGet
    val res = Response(Await.result(HttpMuxer(req)))

    assert(res.contentString === "1.0")
  }

  test("can update the value") {
    val httpVar = new HttpVar("test2", 1.0)
    val req = RequestBuilder().url("http://localhost/vars/test2").buildPost(wrappedBuffer("0.5".getBytes))
    assert(httpVar()() === 1.0)
    val res = Response(Await.result(HttpMuxer(req)))
    assert(res.statusCode === 200)
    assert(res.contentString === "0.5")
    assert(httpVar()() === 0.5)
  }

  test("provides an error when the new value is out of range") {
    val httpVar = new HttpVar("test3", 1.0)
    val req = RequestBuilder().url("http://localhost/vars/test3").buildPost(wrappedBuffer("5".getBytes))
    val res = Response(Await.result(HttpMuxer(req)))
    assert(res.statusCode === 400)
    assert(res.contentString === "invalid rate")
    assert(httpVar()() === 1.0)
  }

  test("provides an error when the new value invald") {
    val httpVar = new HttpVar("test4", 1.0)
    val req = RequestBuilder().url("http://localhost/vars/test4").buildPost(wrappedBuffer("foo".getBytes))
    val res = Response(Await.result(HttpMuxer(req)))
    assert(res.statusCode === 500)
    assert(httpVar()() === 1.0)
  }
}
