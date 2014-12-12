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

import com.twitter.cassie.codecs.Codec
import com.twitter.cassie.connection.ClientProvider
import com.twitter.cassie.util.ByteBufferUtil.EMPTY
import com.twitter.cassie.util.FutureUtil.timeFutureWithFailures
import com.twitter.finagle.stats.{ StatsReceiver, NullStatsReceiver }
import org.slf4j.LoggerFactory
import com.twitter.util.Future
import java.nio.ByteBuffer
import java.util.Collections.{ singleton => singletonJSet }
import java.util.{ArrayList => JArrayList,HashMap => JHashMap,Iterator => JIterator,List => JList,Map => JMap,Set => JSet}
import org.apache.cassandra.finagle.thrift
import scala.collection.JavaConversions._

object SuperCounterColumnFamily {
  private implicit val log = LoggerFactory.getLogger(this.getClass)
}

/*
 * Note that this implementation is the minimal set we've needed in production. We've done this
 * because we hope that SuperColumns can be obsoleted in the future.
 */
case class SuperCounterColumnFamily[Key, Name, SubName](
  keyspace: String,
  name: String,
  provider: ClientProvider,
  keyCodec: Codec[Key],
  nameCodec: Codec[Name],
  subNameCodec: Codec[SubName],
  stats: StatsReceiver,
  readConsistency: ReadConsistency = ReadConsistency.Quorum,
  writeConsistency: WriteConsistency = WriteConsistency.One
) extends BaseColumnFamily(keyspace, name, provider, stats) {

  import SuperCounterColumnFamily._
  import BaseColumnFamily._

  type This = SuperCounterColumnFamily[Key, Name, SubName]

  def consistency(rc: ReadConsistency): This = copy(readConsistency = rc)
  def consistency(wc: WriteConsistency): This = copy(writeConsistency = wc)

  def multigetSlices(keys: JSet[Key], start: Name, end: Name): Future[JMap[Key, JMap[Name, JMap[SubName, CounterColumn[SubName]]]]] = {
    Future {
      val pred = sliceRangePredicate(Some(start), Some(end), Order.Normal, Int.MaxValue)
      multigetSlice(keys, pred)
    }.flatten
  }

  private def sliceRangePredicate(startColumnName: Option[Name], endColumnName: Option[Name], order: Order, count: Int) = {
    val startBytes = startColumnName.map { c => nameCodec.encode(c) }.getOrElse(EMPTY)
    val endBytes = endColumnName.map { c => nameCodec.encode(c) }.getOrElse(EMPTY)
    val pred = new thrift.SlicePredicate()
    pred.setSlice_range(new thrift.SliceRange(startBytes, endBytes, order.reversed, count))
  }

  private def multigetSlice(keys: JSet[Key], pred: thrift.SlicePredicate): Future[JMap[Key, JMap[Name, JMap[SubName, CounterColumn[SubName]]]]] = {
    val cp = new thrift.ColumnParent(name)
    val encodedKeys = keyCodec.encodeSet(keys)
    withConnection(
      "multiget_slice",
      Map("keys" -> encodedKeys, "predicate" -> annPredCodec.encode(pred), "readconsistency" -> readConsistency.toString),
      Seq(keyspace, keys, cp, pred, readConsistency.level)
    ) {
      _.multiget_slice(encodedKeys, cp, pred, readConsistency.level)
    }.map { result =>
      val rows: JMap[Key, JMap[Name, JMap[SubName, CounterColumn[SubName]]]] = new JHashMap(result.size)
      for (rowEntry <- collectionAsScalaIterable(result.entrySet)) {
        val sCols: JMap[Name, JMap[SubName, CounterColumn[SubName]]] = new JHashMap(rowEntry.getValue.size)
        for (scol <- collectionAsScalaIterable(rowEntry.getValue)) {
          val cols: JMap[SubName, CounterColumn[SubName]] = new JHashMap(scol.getCounter_super_column.columns.size)
          for (counter <- collectionAsScalaIterable(scol.getCounter_super_column().columns)) {
            val col = CounterColumn.convert(subNameCodec, counter)
            cols.put(col.name, col)
          }
          sCols.put(nameCodec.decode(scol.getCounter_super_column.BufferForName()), cols)
        }
        rows.put(keyCodec.decode(rowEntry.getKey), sCols)
      }
      rows
    }
  }

  def batch(): SuperCounterBatchMutationBuilder[Key, Name, SubName] =
    new SuperCounterBatchMutationBuilder(this)

  private[cassie] def batch(mutations: JMap[ByteBuffer, JMap[String, JList[thrift.Mutation]]]) = {
    withConnection(
      "batch_mutate",
      Map("writeconsistency" -> writeConsistency.toString),
      Seq(keyspace, mutations, writeConsistency.level)
    ) {
      _.batch_mutate(mutations, writeConsistency.level)
    }
  }
}
