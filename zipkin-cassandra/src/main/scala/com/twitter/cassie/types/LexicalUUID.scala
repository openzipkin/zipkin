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

package com.twitter.cassie.types

import com.twitter.cassie.clocks.Clock
import com.twitter.cassie.FNV1A
import java.net.InetAddress.{ getLocalHost => localHost }
import java.nio.ByteBuffer
import org.apache.commons.codec.binary.Hex.decodeHex

object LexicalUUID {
  private val defaultWorkerID = FNV1A(localHost.getHostName.getBytes)

  /**
   * Given a clock, generates a new LexicalUUID, using a hash of the machine's
   * hostname as a worker ID.
   */
  def apply(clock: Clock): LexicalUUID =
    new LexicalUUID(clock, LexicalUUID.defaultWorkerID)

  /**
   * Given a UUID formatted as a hex string, returns it as a LexicalUUID.
   */
  def apply(uuid: String): LexicalUUID = {
    val buf = ByteBuffer.wrap(decodeHex(uuid.toCharArray.filterNot { _ == '-' }))
    new LexicalUUID(buf.getLong(), buf.getLong())
  }
}

/**
 * A 128-bit UUID, composed of a 64-bit timestamp and a 64-bit worker ID.
 */
case class LexicalUUID(timestamp: Long, workerID: Long) extends Ordered[LexicalUUID] {

  /**
   * Given a worker ID and a clock, generates a new LexicalUUID. If each node
   * has unique worker ID and a clock which is guaranteed to never go backwards,
   * then each generated UUID will be unique.
   */
  def this(clock: Clock, workerID: Long) = this(clock.timestamp, workerID)

  /**
   * Given a clock, generates a new LexicalUUID, using a hash of the machine's
   * hostname as a worker ID.
   */
  def this(clock: Clock) = this(clock.timestamp, LexicalUUID.defaultWorkerID)

  /**
   * Sort by timestamp, then by worker ID.
   */
  def compare(that: LexicalUUID) = {
    val res = timestamp.compare(that.timestamp)
    if (res == 0) {
      workerID.compare(that.workerID)
    } else {
      res
    }
  }

  override def toString = {
    val hex = "%016x".format(timestamp)
    "%s-%s-%s-%016x".format(hex.substring(0, 8),
      hex.substring(8, 12),
      hex.substring(12, 16), workerID)
  }
}
