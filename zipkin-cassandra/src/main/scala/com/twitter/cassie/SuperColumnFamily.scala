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

import com.twitter.cassie.clocks.{ MicrosecondEpochClock, Clock }
import com.twitter.cassie.codecs.{ Codec }
import com.twitter.cassie.connection.ClientProvider
import com.twitter.cassie.util.ByteBufferUtil.EMPTY
import com.twitter.cassie.util.FutureUtil.timeFutureWithFailures
import com.twitter.finagle.stats.StatsReceiver
import org.slf4j.LoggerFactory
import com.twitter.util.Future
import java.nio.ByteBuffer
import java.util.Collections.{ singleton => singletonJSet }
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList, Map => JMap, Set => JSet }
import org.apache.cassandra.finagle.thrift
import scala.collection.JavaConversions._ // TODO get rid of this

/**
 * A readable, writable column family with batching capabilities. This is a
 * lightweight object: it inherits a connection pool from the Keyspace.
 *
 * Note that this implementation is the minimal set we've needed in production. We've done this
 * because we hope that SuperColumns can be obsoleted in the future.
 */
object SuperColumnFamily {
  private implicit val log = LoggerFactory.getLogger(this.getClass)
}

case class SuperColumnFamily[Key, Name, SubName, Value](
  keyspace: String,
  name: String,
  provider: ClientProvider,
  keyCodec: Codec[Key],
  nameCodec: Codec[Name],
  subNameCodec: Codec[SubName],
  valueCodec: Codec[Value],
  stats: StatsReceiver,
  readConsistency: ReadConsistency = ReadConsistency.Quorum,
  writeConsistency: WriteConsistency = WriteConsistency.Quorum
) extends BaseColumnFamily(keyspace, name, provider, stats) {

  import SuperColumnFamily._
  import BaseColumnFamily._

  type This = SuperColumnFamily[Key, Name, SubName, Value]

  private[cassie] var clock: Clock = MicrosecondEpochClock

  def consistency(rc: ReadConsistency): This = copy(readConsistency = rc)
  def consistency(wc: WriteConsistency): This = copy(writeConsistency = wc)

  def insert(key: Key, superColumn: Name, column: Column[SubName, Value]): Future[Void] = {
    Future {
      val cp = (new thrift.ColumnParent(name)).setSuper_column(nameCodec.encode(superColumn))
      val col = Column.convert(subNameCodec, valueCodec, clock, column)
      val keyEncoded = keyCodec.encode(key)
      withConnection(
        "insert",
        Map("key" -> keyEncoded, "col" -> col.name, "writeconsistency" -> writeConsistency.toString),
        Seq(keyspace, key, cp, col, writeConsistency.level)
      ) {
        _.insert(keyEncoded, cp, col, writeConsistency.level)
      }
    }.flatten
  }

  def getRow(key: Key): Future[Seq[(Name, Seq[Column[SubName, Value]])]] = {
    getRowSlice(key, None, None, Int.MaxValue, Order.Normal)
  }

  def getRowSlice(key: Key, start: Option[Name], end: Option[Name], count: Int,
    order: Order): Future[Seq[(Name, Seq[Column[SubName, Value]])]] = {
    Future {
      getOrderedSlice(key, start, end, count, order)
    }.flatten
  }

  def multigetRow(keys: JSet[Key]): Future[JMap[Key, Seq[(Name, Seq[Column[SubName, Value]])]]] = {
    multigetRowSlice(keys, None, None, Int.MaxValue, Order.Normal)
  }

  def multigetRowSlice(keys: JSet[Key], start: Option[Name], end: Option[Name], count: Int,
    order: Order): Future[JMap[Key, Seq[(Name, Seq[Column[SubName, Value]])]]] = {
    Future {
      multigetSlice(keys, start, end, count, order)
    }.flatten
  }

  private def getOrderedSlice(key: Key, start: Option[Name], end: Option[Name], size: Int, order: Order): Future[Seq[(Name, Seq[Column[SubName, Value]])]] = {
    Future {
      val pred = sliceRangePredicate(start, end, order, size)
      val cp = new thrift.ColumnParent(name)
      val keyEncoded = keyCodec.encode(key)
      withConnection(
        "get_slice",
        Map("key" -> keyEncoded, "predicate" -> annPredCodec.encode(pred), "readconsistency" -> readConsistency.toString),
        Seq(keyspace, key, cp, pred, readConsistency.level)
      ) {
        _.get_slice(keyEncoded, cp, pred, readConsistency.level)
      } map { result =>
        result.map { cosc =>
          val sc = cosc.getSuper_column()
          (nameCodec.decode(sc.name), sc.columns.map(Column.convert(subNameCodec, valueCodec, _)))
        }
      }
    }.flatten
  }

  private def multigetSlice(keys: JSet[Key], start: Option[Name], end: Option[Name],size: Int,
    order: Order): Future[JMap[Key, Seq[(Name, Seq[Column[SubName, Value]])]]] = {
    val pred = sliceRangePredicate(start, end, order, size)
    val cp = new thrift.ColumnParent(name)
    val keyEncoded = keyCodec.encodeSet(keys)
    withConnection(
      "multiget_slice",
      Map("key" -> keyEncoded, "predicate" -> annPredCodec.encode(pred), "readconsistency" -> readConsistency.toString),
      Seq(keyspace, keys, cp, pred, readConsistency.level)
    ) {
      _.multiget_slice(keyEncoded, cp, pred, readConsistency.level)
    } map { result =>
      val rows: JMap[Key, Seq[(Name, Seq[Column[SubName, Value]])]] = new JHashMap(result.size)
      result.foldLeft(rows) {
        case (memo, (key, coscList)) =>
          memo(keyCodec.decode(key)) = coscList.map { cosc =>
            val sc = cosc.getSuper_column()
            (nameCodec.decode(sc.name), sc.columns.map(Column.convert(subNameCodec, valueCodec, _)))
          }
          memo
      }
    }
  }

  def removeRow(key: Key): Future[Void] = {
    val cp = new thrift.ColumnPath(name)
    val ts = clock.timestamp
    val keyEncoded = keyCodec.encode(key)
    withConnection(
      "remove",
      Map("key" -> keyEncoded, "timestamp" -> ts, "writeconsistency" -> writeConsistency.toString),
      Seq(keyspace, key, cp, ts, writeConsistency.level)
    ) {
      _.remove(keyEncoded, cp, ts, writeConsistency.level)
    }
  }

  private def sliceRangePredicate(startColumnName: Option[Name], endColumnName: Option[Name], order: Order, count: Int) = {
    val startBytes = startColumnName.map { c => nameCodec.encode(c) }.getOrElse(EMPTY)
    val endBytes = endColumnName.map { c => nameCodec.encode(c) }.getOrElse(EMPTY)
    val pred = new thrift.SlicePredicate()
    pred.setSlice_range(new thrift.SliceRange(startBytes, endBytes, order.reversed, count))
  }

  private def sliceRangePredicate(columnNames: JSet[Name]) = {
    new thrift.SlicePredicate().setColumn_names(nameCodec.encodeSet(columnNames))
  }
}
