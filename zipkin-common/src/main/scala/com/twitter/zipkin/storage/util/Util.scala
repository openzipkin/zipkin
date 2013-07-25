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
package com.twitter.zipkin.util

import java.nio.ByteBuffer
import java.util.Arrays

object Util {

  /**
   * Get the contents of a ByteBuffer as an Array
   * Stolen from http://svn.apache.org/viewvc/cassandra/trunk/src/java/org/apache/cassandra/utils/ByteBufferUtil.java?revision=1201726&view=markup
   */
  def getArrayFromBuffer(buf: ByteBuffer): Array[Byte] = {
    val length = buf.remaining
    if (buf.hasArray()) {
      val boff = buf.arrayOffset() + buf.position()
      if (boff == 0 && length == buf.array().length) {
        buf.array()
      } else {
        Arrays.copyOfRange(buf.array(), boff, boff + length)
      }
    } else {
      val bytes = new Array[Byte](length)
      buf.duplicate.get(bytes)
      bytes
    }
  }

}