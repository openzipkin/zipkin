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

import com.twitter.util.Future
import java.util.{ArrayList => JArrayList}
import org.apache.cassandra.finagle.thrift

class SuperCounterBatchMutationBuilder[Key, Name, SubName](cf: SuperCounterColumnFamily[Key, Name, SubName]) extends BatchMutation {

  def insert(key: Key, name: Name, column: CounterColumn[SubName]) = synchronized {
    putMutation(cf.keyCodec.encode(key), cf.name, insertMutation(key, name, column))
    this
  }

  /**
   * Submits the batch of operations, returning a future to allow blocking for success.
   */
  def execute(): Future[Void] = {
    if (mutations.isEmpty) {
      Future.Void
    } else {
      Future {
        cf.batch(mutations)
      }.flatten
    }
  }

  private[this] def insertMutation(key: Key, name: Name, column: CounterColumn[SubName]): thrift.Mutation = {
    val cosc = new thrift.ColumnOrSuperColumn()
    val counterColumn = new thrift.CounterColumn(cf.subNameCodec.encode(column.name), column.value)
    val columns = new JArrayList[thrift.CounterColumn]()
    columns.add(counterColumn)
    val sc = new thrift.CounterSuperColumn(cf.nameCodec.encode(name), columns)
    cosc.setCounter_super_column(sc)
    val mutation = new thrift.Mutation
    mutation.setColumn_or_supercolumn(cosc)
  }
}
