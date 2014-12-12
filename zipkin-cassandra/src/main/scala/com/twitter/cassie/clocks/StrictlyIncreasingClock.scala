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

package com.twitter.cassie.clocks

import java.util.concurrent.atomic.AtomicLong

/**
 * A concurrent, strictly-increasing clock.
 */
abstract class StrictlyIncreasingClock extends Clock {
  private val counter = new AtomicLong(tick)

  def timestamp: Long = {
    var newTime: Long = 0
    while (newTime == 0) {
      val last = counter.get
      val current = tick
      val next = if (current > last) current else last + 1
      if (counter.compareAndSet(last, next)) {
        newTime = next
      }
    }
    return newTime
  }

  protected def tick: Long
}
