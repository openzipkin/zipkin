/*
 * Copyright 2012 Tumblr Inc.
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

package com.twitter.zipkin.storage.redis

import com.twitter.zipkin.common.Span

/**
 * Represents a range of time
 */
case class TimeRange(first: Long, last: Long) {
  /**
   * Takes the union of the time ranges.
   */
  def widen(other: TimeRange) =
    TimeRange(if (first < other.first) first else other.first,
      if (last < other.last) other.last else last)
}

object TimeRange {
  /**
   * Takes the time range from the start of the first annotation to the end of the last.
   */
  def fromSpan(span: Span): Option[TimeRange] = for (firstAnno <- span.firstAnnotation;
    lastAnno <- span.lastAnnotation)
    yield TimeRange(firstAnno.timestamp, lastAnno.timestamp)
}