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
package com.twitter.zipkin.storage

import com.twitter.util.{Closable, Duration, Future}
import com.twitter.zipkin.common.Span

trait Storage extends Closable {
  /**
   * Store the span in the underlying storage for later retrieval.
   * @return a future for the operation
   */
  def storeSpan(span: Span) : Future[Unit]

  /**
   * Set the ttl of a trace. Used to store a particular trace longer than the
   * default. It must be oh so interesting!
   */
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit]

  /**
   * Get the time to live for a specific trace.
   * If there are multiple ttl entries for one trace, pick the lowest one.
   */
  def getTimeToLive(traceId: Long): Future[Duration]

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]]

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]]
  def getSpansByTraceId(traceId: Long): Future[Seq[Span]]
  /**
   * How long do we store the data before we delete it? In seconds.
   */
  def getDataTimeToLive: Int

}
