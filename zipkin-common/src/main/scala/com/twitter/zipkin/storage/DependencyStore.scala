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
import com.twitter.zipkin.common.{DependencyLink, Dependencies}

/**
 * Storage and retrieval interface for aggregate dependencies that may be computed offline and
 * reloaded into online storage.
 */
abstract class DependencyStore extends java.io.Closeable {

  /**
   * @param startTs  microseconds from epoch, defaults to one day before end_time
   * @param endTs  microseconds from epoch, defaults to now
   * @return dependency links in an interval contained by startTs and endTs,
   *         or empty if none are found
   */
  def getDependencies(startTs: Option[Long], endTs: Option[Long] = None): Future[Seq[DependencyLink]]
  def storeDependencies(dependencies: Dependencies): Future[Unit]
}

class NullDependencyStore extends DependencyStore {

  def close() {}

  def getDependencies(startTs: Option[Long], endTs: Option[Long] = None) = Future.value(Seq.empty)
  def storeDependencies(dependencies: Dependencies): Future[Unit] = Future.Unit
}
