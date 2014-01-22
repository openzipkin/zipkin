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
package com.twitter.zipkin.sampler

class BoundedBuffer[T: ClassManifest](size: Int) {
  private[this] val buffer = new Array[T](size)
  private[this] var curSize = 0
  private[this] var bufferLoc = -1

  def push(newVal: T) = synchronized {
    if (curSize < size) curSize += 1
    bufferLoc = (bufferLoc + 1) % size
    buffer(bufferLoc) = newVal
  }

  def pushAndSnap(newVal: T): Seq[T] = synchronized {
    push(newVal)
    snap
  }

  def snap: Seq[T] = synchronized {
    (0 until curSize) map { n => buffer(((bufferLoc - n) + curSize) % curSize) } toSeq
  }

  def snapEnds: (T, T) = synchronized {
    if (curSize == 0) new Exception("buffer is empty")
    (buffer(bufferLoc), buffer(((bufferLoc - (curSize - 1)) + curSize) % curSize))
  }
}
