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

import com.twitter.cassie.types.LexicalUUID
import java.nio.ByteBuffer

/**
 * Encodes and decodes UUIDs as 128-bit values.
 */
object LexicalUUIDCodec extends Codec[LexicalUUID] {
  private val length = 16

  def encode(uuid: LexicalUUID) = {
    val b = ByteBuffer.allocate(length)
    b.putLong(uuid.timestamp)
    b.putLong(uuid.workerID)
    b.rewind
    b
  }

  def decode(buf: ByteBuffer) = {
    require(buf.remaining == length)
    val dupe = buf.duplicate
    LexicalUUID(dupe.getLong(), dupe.getLong())
  }
}
