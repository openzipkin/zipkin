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

/**
 * Encodes and decodes 32-bit integers as 4-byte, big-endian byte buffers.
 */
object IntCodec extends Codec[Int] {
  private val length = 4

  def encode(v: Int) = {
    val buf = ByteBuffer.allocate(length)
    buf.putInt(v)
    buf.rewind
    buf
  }

  def decode(buf: ByteBuffer) = {
    require(buf.remaining == length)
    buf.duplicate().getInt
  }
}
