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

import java.nio.ByteBuffer
import scala.collection.JavaConversions.collectionAsScalaIterable
import java.util.{ ArrayList => JArrayList, Set => JSet, List => JList }

/**
 * A bidirection encoding for column names or values.
 */
trait Codec[A] {
  def encode(obj: A): ByteBuffer
  def decode(ary: ByteBuffer): A

  /** To conveniently get the singleton/Object from Java. */
  def get() = this

  /** Helpers for conversion from ByteBuffers to byte arrays. Keep explicit! */
  def b2b(buff: ByteBuffer): Array[Byte] = {
    val bytes = new Array[Byte](buff.remaining)
    buff.duplicate.get(bytes)
    bytes
  }
  def b2b(array: Array[Byte]): ByteBuffer = ByteBuffer.wrap(array)

  def encodeSet(values: JSet[A]): JList[ByteBuffer] = {
    val output = new JArrayList[ByteBuffer](values.size)
    for (value <- collectionAsScalaIterable(values))
      output.add(encode(value))
    output
  }
}
