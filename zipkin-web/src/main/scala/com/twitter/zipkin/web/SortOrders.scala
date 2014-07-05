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
package com.twitter.zipkin.web

import com.twitter.zipkin.query.TraceSummary

object SortOrders {

  private val OrderByDurationDesc = {
    (a: TraceSummary, b: TraceSummary) => a.durationMicro > b.durationMicro
  }
  private val OrderByDurationAsc = {
    (a: TraceSummary, b: TraceSummary) => a.durationMicro < b.durationMicro
  }
  private val OrderByTimestampDesc = {
    (a: TraceSummary, b: TraceSummary) => a.startTimestamp > b.startTimestamp
  }
  private val OrderByTimestampAsc = {
    (a: TraceSummary, b: TraceSummary) => a.startTimestamp < b.startTimestamp
  }

  private[this] val orders = Map(
    "duration-desc" ->("Longest First", OrderByDurationDesc),
    "duration-asc" ->("Shortest First", OrderByDurationAsc),
    "timestamp-desc" ->("Newest First", OrderByTimestampDesc),
    "timestamp-asc" ->("Oldest First", OrderByTimestampAsc)
  )

  def getOrderNames(): Map[String, String] = orders.mapValues(_._1)

  def getSortFunction(order: Option[String]): (TraceSummary, TraceSummary) => Boolean = {
    order.flatMap(orders.get).map(_._2).getOrElse(OrderByDurationDesc)
  }

}
