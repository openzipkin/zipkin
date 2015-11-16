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

import com.twitter.util.Future
import com.twitter.zipkin.common.{Dependencies, DependencyLink}

/**
 * Storage and retrieval interface for aggregate dependencies that may be computed offline and
 * reloaded into online storage.
 */
abstract class DependencyStore extends java.io.Closeable {

  /**
   * Returns dependency links derived from spans in the [[SpanStore]].
   *
   * <p/>Implementations may bucket aggregated data, for example daily. When this is the case, endTs
   * may be floored to align with that bucket, for example midnight if daily. lookback applies to
   * the original endTs, even when bucketed. Using the daily example, if endTs was 11pm and lookback
   * was 25 hours, the implementation would query against 2 buckets.
   *
   * @param endTs only return links from spans where [[com.twitter.zipkin.common.Span.timestamp]]
   *              are at or before this time in epoch milliseconds.
   * @param lookback only return links from spans where [[com.twitter.zipkin.common.Span.timestamp]]
   *                 are at or after (endTs - lookback) in milliseconds. Defaults to endTs.
   * @return dependency links in an interval contained by (endTs - lookback) or
   *         empty if none are found
   */
  def getDependencies(endTs: Long, lookback: Option[Long] = None): Future[Seq[DependencyLink]]
  def storeDependencies(dependencies: Dependencies): Future[Unit]
}

class NullDependencyStore extends DependencyStore {

  def close() {}

  def getDependencies(endTs: Long, lookback: Option[Long] = None) = Future.value(Seq.empty)
  def storeDependencies(dependencies: Dependencies): Future[Unit] = Future.Unit
}
