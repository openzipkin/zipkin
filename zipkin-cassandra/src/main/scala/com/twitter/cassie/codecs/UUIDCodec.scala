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

import com.twitter.cassie.util.ByteBufferUtil
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Encodes and decodes UUIDs as 128-bit values.
 */
object UUIDCodec extends Codec[UUID] {
  private val length = 16
  private val empty = new UUID(0, 0)

  def encode(uuid: UUID) = {
    // NOTE: we do not want an empty UUID to count in a range check
    if (!uuid.equals(empty)) {
      val b = ByteBuffer.allocate(length)
      b.putLong(uuid.getMostSignificantBits)
      b.putLong(uuid.getLeastSignificantBits)
      b.rewind
      b
    } else {
      ByteBufferUtil.EMPTY
    }
  }

  def decode(buf: ByteBuffer) = {
    // NOTE: RowsIteratee creates these with empty byte buffers
    if (buf.remaining == length) {
      val dupe = buf.duplicate
      new UUID(dupe.getLong(), dupe.getLong())
    } else {
      empty
    }
  }
}
