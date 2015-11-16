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

import scala.util.hashing.MurmurHash3

/**
 * A representation of the fact that one service calls another. These should be unique across
 * the set of (parent, child)
 *
 * @param _parent the calling [[com.twitter.zipkin.common.Endpoint.serviceName]]
 * @param _child the callee's [[com.twitter.zipkin.common.Endpoint.serviceName]]
 * @param callCount calls made during the duration (in milliseconds) of this link
 */
// This is not a case-class as we need to enforce serviceName and spanName as lowercase
class DependencyLink(_parent: String, _child: String, val callCount: Long) {
  /** the calling [[com.twitter.zipkin.common.Endpoint.serviceName]] */
  val parent: String = _parent.toLowerCase

  /** the callee's [[com.twitter.zipkin.common.Endpoint.serviceName]] */
  val child: String = _child.toLowerCase

  override def toString = "%s->%s(%d)".format(parent, child, callCount)

  override def hashCode = MurmurHash3.seqHash(List(parent, child, callCount))

  override def equals(other: Any) = other match {
    case x: DependencyLink => x.parent == parent && x.child == child && x.callCount == callCount
    case _ => false
  }

  def copy(
    parent: String = this.parent,
    child: String = this.child,
    callCount: Long = this.callCount
  ) = DependencyLink(parent, child, callCount)
}

object DependencyLink {
  def apply(parent: String, child: String, callCount: Long) =
    new DependencyLink(parent, child, callCount)
}

/**
 * This represents all dependencies across all services over a given time period.
 *
 * @param startTs milliseconds from epoch
 * @param endTs milliseconds from epoch
 * @param links link information for every dependent service
 */
case class Dependencies(startTs: Long, endTs: Long, links: Seq[DependencyLink]) {
  // used for summing/merging database rows
  def +(that: Dependencies): Dependencies = {
    // don't sum against Dependencies.zero
    if (that == Dependencies.zero) {
      return this
    } else if (this == Dependencies.zero) {
      return that
    }

    // new start/end should be the inclusive time span of both items
    val newStart = startTs min that.startTs
    val newEnd = endTs max that.endTs

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

  def toLinks(spans: Seq[Span]): Seq[DependencyLink] = {
    val flattenedSpans: Map[(Long, Long), Span] = spans
      .filter(_.serviceName.isDefined)
      .groupBy(s => (s.id, s.traceId))
      .mapValues(spans => spans.reduce((s1, s2) => s1.merge(s2)))

    val childCountByParent: Map[(Long, Long), Map[String, Long]] = flattenedSpans
      .filter { case (key, span) => span.parentId.isDefined }
      .mapValues(span => ((span.parentId.get, span.traceId), span))
      .groupBy(_._2._1) // group by parentId, traceId
      .mapValues(_.values.map(_._2.serviceName.get).toList) // convert values to serviceNames
      .mapValues(serviceNames => serviceNames.groupBy(identity).mapValues(_.size)) // count serviceNames

    val parentToChildCount: Map[(String, String), Long] = flattenedSpans // join on key
      .map { case (key, span) => span.serviceName.get -> childCountByParent.getOrElse(key, Map.empty) }
      .flatMap { case (parent, childServiceNameCount) => // Convert from Map of Maps to something we can sum
        childServiceNameCount.map { case (child, count) => ((parent, child), count) }
      }
      .groupBy(_._1).mapValues(_.map(_._2).seq.sum) // collect and sum entries with same parent, child

    parentToChildCount.map {
      case (parentChild, count) => DependencyLink(parentChild._1, parentChild._2, count)
    }.toSeq
  }
}
