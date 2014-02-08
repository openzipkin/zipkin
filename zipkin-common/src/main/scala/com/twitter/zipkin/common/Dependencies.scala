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

import com.twitter.util.Time
import com.twitter.algebird.{Monoid, Semigroup, Moments}

/**
 * Abstraction of a service
 */
case class Service(name: String)

/**
 * A representation of the fact that one service calls another.  These should be unique across
 * the set of (parent, child)
 * @param parent the calling service
 * @param child the service being called
 * @param durationMoments moments describing the distribution of durations (in microseconds) for this link
 */
case class DependencyLink(parent: Service, child: Service, durationMoments: Moments)

object DependencyLink {
  // this gives us free + operator along with other algebird aggregation methods
  implicit val sg:Semigroup[DependencyLink] = new Semigroup[DependencyLink] {
    def plus(l: DependencyLink, r: DependencyLink) = {
      assert(l.child == r.child && l.parent == r.parent)
      DependencyLink(l.parent, l.child, Monoid.plus(l.durationMoments, r.durationMoments))
    }
  }
}

/**
 * This represents all dependencies across all services over a given time period.
 * @param startTime the startTime time for this period
 * @param endTime how long the period lasted
 * @param links link information for every dependent service
 */
case class Dependencies(
  startTime: Time,
  endTime: Time,
  links: Seq[DependencyLink]
)

object Dependencies {
  // used for summing/merging database rows
  implicit val sg:Semigroup[Dependencies] = new Semigroup[Dependencies] {
    def plus(l: Dependencies, r: Dependencies) = {
      // new start/end should be the inclusive time span of both items
      val newStart = r.startTime min l.startTime
      val newEnd = r.endTime max l.endTime

      // links are merged by mapping to parent/child and summing corresponding links
      val lLinkMap = l.links.map { link => (link.parent, link.child) -> link }.toMap
      val rLinkMap = r.links.map { link => (link.parent, link.child) -> link }.toMap
      val newLinks = Semigroup.plus(rLinkMap, lLinkMap).values.toSeq

      Dependencies(newStart, newEnd, newLinks)
    }
  }

  val zero = Dependencies(Time.Top, Time.Bottom, Seq.empty[DependencyLink])
}
