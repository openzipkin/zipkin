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
package com.twitter.zipkin.storage

import collection.mutable.ArrayBuffer
import com.twitter.util.Future

/**
 * Collect trace ids and fetch their durations in batches as the trace ids come in.
 */
class TraceIdsToDuration(index: Index, batchSize: Int) {
  val ids = new ArrayBuffer[Long]
  val durationFutures = new ArrayBuffer[Future[Seq[TraceIdDuration]]]

  /**
   * Add an id to fetch durations for. If we have a batch send it off
   * to fetch the duration data.
   */
  def append(id: Long) = synchronized {
    ids.append(id)
    if (ids.size >= batchSize) {
      durationFutures.append(index.getTracesDuration(ids.distinct))
      ids.clear()
    }
  }

  /**
   * Once we have added all the ids send off the final batch and
   * return a future of the trace durations.
   */
  def getDurations(): Future[Seq[TraceIdDuration]] = synchronized {
    // get the last batch
    durationFutures.append(index.getTracesDuration(ids.distinct))
    val durations = Future.collect(durationFutures).map(_.flatten)
    durations.map(_.distinct)
  }

  /**
   * Clear if you want to reuse this
   */
  def clear() = synchronized {
    ids.clear()
    durationFutures.clear()
  }
}