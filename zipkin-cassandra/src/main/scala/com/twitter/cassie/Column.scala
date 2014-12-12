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

import com.twitter.cassie.clocks.Clock
import com.twitter.cassie.codecs.Codec
import com.twitter.conversions.time._
import com.twitter.util.Duration
import org.apache.cassandra.finagle.thrift

object Column {
  def apply[A, B](name: A, value: B): Column[A, B] = new Column(name, value)

  /**
   * Convert from a thrift CoSC to a Cassie column.
   */
  private[cassie] def convert[A, B](nameCodec: Codec[A], valueCodec: Codec[B], colOrSCol: thrift.ColumnOrSuperColumn): Column[A, B] = {
    val c = Column(
      nameCodec.decode(colOrSCol.column.name),
      valueCodec.decode(colOrSCol.column.value)
    ).timestamp(colOrSCol.column.timestamp)

    if (colOrSCol.column.isSetTtl) {
      c.ttl(colOrSCol.column.getTtl.seconds)
    } else {
      c
    }
  }

  /**
   * Convert from a thrift CoSC to a Cassie column.
   */
  private[cassie] def convert[A, B](nameCodec: Codec[A], valueCodec: Codec[B], column: thrift.Column): Column[A, B] = {
    val c = Column(
      nameCodec.decode(column.name),
      valueCodec.decode(column.value)
    ).timestamp(column.timestamp)

    if (column.isSetTtl) {
      c.ttl(column.getTtl.seconds)
    } else {
      c
    }
  }

  /**
   * Convert from a cassie Column to a thrift.Column
   */
  private[cassie] def convert[A, B](nameCodec: Codec[A], valueCodec: Codec[B], clock: Clock, col: Column[A, B]): thrift.Column = {
    val tColumn = new thrift.Column(nameCodec.encode(col.name))
    tColumn.setValue(valueCodec.encode(col.value))
    tColumn.setTimestamp(col.timestamp.getOrElse(clock.timestamp))
    col.ttl.foreach { t => tColumn.setTtl(t.inSeconds) }
    tColumn
  }
}

case class Column[A, B](name: A, value: B, timestamp: Option[Long], ttl: Option[Duration]) {

  def this(name: A, value: B) = {
    this(name, value, None, None)
  }

  /**
   * Create a copy of this column with a timestamp set. Builder-style.
   */
  def timestamp(ts: Long): Column[A, B] = {
    copy(timestamp = Some(ts))
  }

  /**
   * Create a copy of this Column with a ttl set. Builder-style.
   */
  def ttl(t: Duration): Column[A, B] = {
    copy(ttl = Some(t))
  }

  def pair = name -> this
}
