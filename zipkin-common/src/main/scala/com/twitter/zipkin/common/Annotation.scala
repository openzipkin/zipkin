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
package com.twitter.zipkin.common

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering.natural
import java.util.Comparator

/**
 * Associates an event that explains latency with a timestamp.
 *
 * <p/>Unlike log statements, annotations are often codes: Ex. [[com.twitter.zipkin.Constants.ServerRecv]]("sr").
 *
 * @param timestamp microseconds from epoch
 * @param value     usually a short tag indicating an event, like [[com.twitter.zipkin.Constants.ServerRecv]]("sr") or "finagle.retry"
 * @param host      The host that recorded [[value]], primarily for query by service name.
 */
case class Annotation(timestamp: Long, value: String, host: Option[Endpoint])
  extends Ordered[Annotation] {
  def serviceName = host.map(_.serviceName).getOrElse("")

  /**
   * @return diff between timestamps of the two annotations.
   */
  def -(annotation: Annotation): Long = timestamp - annotation.timestamp

  private[this] val nullsFirst: Comparator[Endpoint] = natural().nullsFirst()

  override def compare(that: Annotation) = ComparisonChain.start()
    .compare(timestamp, that.timestamp)
    .compare(value, that.value)
    .compare(host.orNull, that.host.orNull, nullsFirst)
    .result()
}
