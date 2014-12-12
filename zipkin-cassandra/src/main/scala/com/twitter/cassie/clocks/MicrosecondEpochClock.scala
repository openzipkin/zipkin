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

/**
 * A clock which returns the time since Jan 1, 1970 UTC in microseconds.
 *
 * N.B.: This doesn't actually return microseconds, since few platforms actually
 * have reliable access to microsecond-accuracy clocks. What it does return is
 * the time in milliseconds, multiplied by 1000. That said, it *is* strictly
 * increasing, so that even if your calls to MicrosecondEpochClock#timestamp
 * occur within a single millisecond, the timestamps will be ordered
 * appropriately.
 */
object MicrosecondEpochClock extends StrictlyIncreasingClock {
  protected def tick = System.currentTimeMillis * 1000
}
