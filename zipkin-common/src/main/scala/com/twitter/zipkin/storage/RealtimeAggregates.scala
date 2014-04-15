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

import com.twitter.algebird.Monoid
import com.twitter.util.{Closable, CloseAwaitably, Time, Future}
import com.twitter.zipkin.common.Dependencies

/**
 * Storage and retrieval interface for realtime aggregates that are computed online
 * and write into online storage
 */
trait RealtimeAggregates extends Closable with CloseAwaitably {
  def getSpanDurations(
    timeStamp: Time,
    serverServiceName: String,
    rpcName: String
  ): Future[Map[String, List[Long]]]
}

object NullRealtimeAggregates extends RealtimeAggregates {
  def close(deadline: Time): Future[Unit] = closeAwaitably {
    Future.Done
  }

  def getSpanDurations(timeStamp: Time, serverServiceName: String, rpcName: String) =
    Future(Map.empty[String, List[Long]])
}
