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
package com.twitter.zipkin.collector.sampler.adaptive

import com.twitter.ostrich.stats.Counter

/**
 * Data structure that keeps the latest _length_ Longs added to it. Adding
 * more than _length_ entries will result in the oldest to be dropped.
 */
trait BoundedBuffer {

  val maxLength: Int

  protected var buf: Seq[Double] = Seq.empty

  def update(t: Double) {
    buf.synchronized {
      buf = (t +: buf) match {
        case w if w.length > maxLength => w.init
        case w @ _ => w
      }
    }
  }

  def latest: Double = {
    buf.synchronized {
      buf match {
        case Nil => 0
        case l :: rest => l
      }
    }
  }

  def underlying: Seq[Double] = buf.synchronized { buf }

  def length: Int = buf.synchronized { buf.length }
}

/**
 * Used to keep counter values over a window of time to find the increase
 * over that period of time.
 */
class SlidingWindowCounter(counter: Counter, length: Int)
  extends BoundedBuffer {

  val maxLength = length

  def update() {
    super.update(counter())
  }

  def apply() = buf.synchronized {
    buf.head - buf.last
  }
}
