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
case class Dependencies(startTs: Long, endTs: Long, links: Seq[DependencyLink])
