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

import com.twitter.cassie.codecs.{ ThriftCodec, Codec }
import com.twitter.cassie.connection.ClientProvider
import com.twitter.cassie.util.FutureUtil.timeFutureWithFailures
import com.twitter.finagle.stats.{ StatsReceiver, NullStatsReceiver }
import com.twitter.util.Future
import java.nio.ByteBuffer
import java.util.{ HashMap => JHashMap, Map => JMap, List => JList, ArrayList => JArrayList }
import org.apache.cassandra.finagle.thrift
import scala.collection.JavaConversions._

/**
 * A Cassandra keyspace, which maintains a connection pool.
 *
 * @param provider a [[com.twitter.cassie.connection.ClientProvider]] instance
 */
class Keyspace(val name: String, val provider: ClientProvider, val stats: StatsReceiver) {

  /**
   * Returns a ColumnFamily with the given name and column/value codecs.
   */
  def columnFamily[Key, Name, Value](
    name: String,
    keyCodec: Codec[Key],
    nameCodec: Codec[Name],
    valueCodec: Codec[Value]) =
    new ColumnFamily(this.name, name, provider, keyCodec, nameCodec, valueCodec, stats.scope(name))

  /**
   * Returns a CounterColumnFamily with the given name and column codecs
   */
  def counterColumnFamily[Key, Name](
    name: String,
    keyCodec: Codec[Key],
    nameCodec: Codec[Name]) =
    new CounterColumnFamily(this.name, name, provider, keyCodec, nameCodec, stats.scope(name))

  def superColumnFamily[Key, Name, SubName, Value](
    name: String,
    keyCodec: Codec[Key],
    nameCodec: Codec[Name],
    subNameCodec: Codec[SubName],
    valueCodec: Codec[Value]) = new SuperColumnFamily(this.name, name, provider, keyCodec, nameCodec, subNameCodec, valueCodec, stats.scope(name))

  def superCounterColumnFamily[Key, Name, SubName](
    name: String,
    keyCodec: Codec[Key],
    nameCodec: Codec[Name],
    subNameCodec: Codec[SubName]) = new SuperCounterColumnFamily(this.name, name, provider, keyCodec, nameCodec, subNameCodec, stats.scope(name))

  /**
   * Execute batch mutations across column families. To use this, build a separate BatchMutationBuilder
   *   for each CF, then send them all to this method.
   * @return a future that can contain [[org.apache.cassandra.finagle.thrift.TimedOutException]],
   *  [[org.apache.cassandra.finagle.thrift.UnavailableException]] or [[org.apache.cassandra.finagle.thrift.InvalidRequestException]]
   * @param batches a Seq of BatchMutationBuilders, each for a different CF. Their mutations will be merged and
   *   sent as one operation
   * @param writeConsistency to write this at
   */
  def execute(batches: Iterable[BatchMutation], writeConsistency: WriteConsistency): Future[Void] = {
    if (batches.size == 0) return Future.Void

    val mutations = new JHashMap[ByteBuffer, JMap[String, JList[thrift.Mutation]]]

    batches.map(_.mutations).foreach { ms =>
      for ((row, inner) <- ms) {
        if (!mutations.containsKey(row)) {
          mutations.put(row, new JHashMap[String, JList[thrift.Mutation]])
        }
        val oldRowMap = mutations.get(row)
        for ((cf, mutationList) <- inner) {
          if (!oldRowMap.containsKey(cf)) {
            oldRowMap.put(cf, new JArrayList[thrift.Mutation])
          }
          val oldList = oldRowMap.get(cf)
          oldList.addAll(mutationList)
        }
      }
    }

    timeFutureWithFailures(stats, "batch_execute") {
      provider.map { _.batch_mutate(mutations, writeConsistency.level) }
    }
  }

  /**
   * Closes connections to the cluster for this keyspace.
   */
  def close() = provider.close()
}
