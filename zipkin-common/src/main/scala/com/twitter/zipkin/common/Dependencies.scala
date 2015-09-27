/*
 * Copyright 2013 Twitter Inc.
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

/**
 * A representation of the fact that one service calls another. These should be unique across
 * the set of (parent, child)
 * @param parent the calling service
 * @param child the service being called
 * @param callCount calls made during the duration (in microseconds) of this link
 */
case class DependencyLink(parent: String, child: String, callCount: Long)

/**
 * This represents all dependencies across all services over a given time period.
 * @param startTime microseconds from epoch
 * @param endTime microseconds from epoch
 * @param links link information for every dependent service
 */
case class Dependencies(startTime: Long, endTime: Long, links: Seq[DependencyLink]) {
  // used for summing/merging database rows
  def +(that: Dependencies): Dependencies = {
    // don't sum against Dependencies.zero
    if (that == Dependencies.zero) {
      return this
    } else if (this == Dependencies.zero) {
      return that
    }

    // new start/end should be the inclusive time span of both items
    val newStart = startTime min that.startTime
    val newEnd = endTime max that.endTime

    // links are merged by mapping to parent/child and summing corresponding links
    val lLinkMap = that.links.map { link => (link.parent, link.child) -> link }.toMap
    val rLinkMap = links.map { link => (link.parent, link.child) -> link }.toMap

    val merged = lLinkMap.toSeq ++ rLinkMap.toSeq
    val newLinks = merged.groupBy(_._1) // group by parent/child
      .mapValues(_.map(_._2).toList) // concatenate values
      .map({
      case ((parent, child), links) => DependencyLink(parent, child, links.map(_.callCount).sum)
    }).toSeq
    Dependencies(newStart, newEnd, newLinks)
  }
}
object Dependencies {
  val zero = Dependencies(0, 0, Seq.empty[DependencyLink])
}
