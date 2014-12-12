// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.codecs

import org.apache.thrift._
import org.apache.thrift.protocol._
import org.apache.thrift.transport._
import java.nio.ByteBuffer
import java.io._

// TODO move to util-thrift
class ThriftCodec[T <: TBase[_, _]](klass: Class[T]) extends Codec[T] {

  class ThreadLocal[T](init: => T) extends java.lang.ThreadLocal[T] {
    override def initialValue: T = init
  }
  implicit def getThreadLocal[S](tl: ThreadLocal[S]): S = tl.get

  val thriftProtocolFactory = new ThreadLocal(new TBinaryProtocol.Factory())
  val outputStream = new ThreadLocal(new ByteArrayOutputStream())
  val outputProtocol = new ThreadLocal(thriftProtocolFactory.getProtocol(new TIOStreamTransport(outputStream)))
  val inputStream = new ThreadLocal(new ByteArrayInputStream(Array.empty[Byte]) {
    def refill(ary: Array[Byte]) {
      buf = ary
      pos = 0
      mark = 0
      count = buf.length
    }
  })
  val inputProtocol = new ThreadLocal(thriftProtocolFactory.getProtocol(new TIOStreamTransport(inputStream)))

  def encode(t: T) = {
    outputStream.reset
    t.write(outputProtocol)
    b2b(outputStream.toByteArray)
  }

  def decode(ary: ByteBuffer) = {
    inputStream.refill(b2b(ary))
    val out = klass.newInstance
    out.read(inputProtocol)
    out
  }
}
