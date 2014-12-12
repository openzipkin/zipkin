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
import org.apache.cassandra.finagle.thrift

object CounterColumn {

  /**
   * Convert from a thrift.CounterColumn to a cassie CounterColumn
   */
  private[cassie] def convert[A](nameCodec: Codec[A], counter: thrift.CounterColumn): CounterColumn[A] = {
    CounterColumn(
      nameCodec.decode(counter.name),
      counter.value
    )
  }

  /**
   * Convert from a thrift.CounterColumn to a cassie CounterColumn
   */
  private[cassie] def convert[A](nameCodec: Codec[A], cosc: thrift.ColumnOrSuperColumn): CounterColumn[A] = {
    val counter = cosc.getCounter_column
    CounterColumn(
      nameCodec.decode(counter.name),
      counter.value
    )
  }

  /**
   * Convert from a cassie CounterColumn to a thrift CounterColumn
   */
  private[cassie] def convert[A](nameCodec: Codec[A], col: CounterColumn[A]): thrift.CounterColumn = {
    new thrift.CounterColumn(
      nameCodec.encode(col.name),
      col.value
    )
  }
}

/**
 * A counter column in a Cassandra. Belongs to a row in a column family.
 */
case class CounterColumn[A](name: A, value: Long) {
  def pair = name -> this
}
