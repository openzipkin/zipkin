// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie

import org.apache.cassandra.finagle.thrift

/**
 * The level of consistency required for a write operation.
 */
sealed case class WriteConsistency(level: thrift.ConsistencyLevel) {
  override def toString = "WriteConsistency." +
    level.toString.toLowerCase.capitalize
}

object WriteConsistency {
  /**
   * Ensure that the write has been written to at least 1 node, including hinted
   * recipients.
   */
  val Any = WriteConsistency(thrift.ConsistencyLevel.ANY)

  /**
   * Ensure that the write has been written to at least 1 node's commit log and
   * memory table before responding to the client.
   */
  val One = WriteConsistency(thrift.ConsistencyLevel.ONE)

  /**
   * Ensure that the write has been written to ReplicationFactor / 2 + 1 nodes
   * before responding to the client.
   */
  val Quorum = WriteConsistency(thrift.ConsistencyLevel.QUORUM)

  /**
   * Returns the record with the most recent timestamp once a majority of replicas within
   * the local datacenter have replied. Requres NetworkTopologyStrategy on the server side.
   */
  val LocalQuorum = WriteConsistency(thrift.ConsistencyLevel.LOCAL_QUORUM)

  /**
   * Returns the record with the most recent timestamp once a majority of replicas within
   * each datacenter have replied.
   */
  val EachQuorum = WriteConsistency(thrift.ConsistencyLevel.EACH_QUORUM)

  /**
   * Ensure that the write is written to all ReplicationFactor nodes before
   * responding to the client. Any unresponsive nodes will fail the operation.
   */
  val All = WriteConsistency(thrift.ConsistencyLevel.ALL)
}
