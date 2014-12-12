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
import com.twitter.util.Future
import java.util.Collections.{ singleton => singletonJSet }
import java.util.{Set => JSet}
import org.apache.cassandra.finagle.thrift

/**
 * A ColumnFamily-alike which batches mutations into a single API call for counters.
 */
class CounterBatchMutationBuilder[Key, Name](cf: CounterColumnFamily[Key, Name])
  extends BatchMutation {

  type This = CounterBatchMutationBuilder[Key, Name]

  def insert(key: Key, column: CounterColumn[Name]): This = synchronized {
    putMutation(cf.keyCodec.encode(key), cf.name, insertMutation(key, column))
    this
  }

  def removeColumn(key: Key, columnName: Name): This =
    removeColumns(key, singletonJSet(columnName))

  def removeColumns(key: Key, columnNames: JSet[Name]): This = synchronized {
    putMutation(cf.keyCodec.encode(key), cf.name, deleteMutation(key, columnNames))
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

  private[this] def insertMutation(key: Key, column: CounterColumn[Name]): thrift.Mutation = {
    val cosc = new thrift.ColumnOrSuperColumn()
    val counterColumn = new thrift.CounterColumn(cf.nameCodec.encode(column.name), column.value)
    cosc.setCounter_column(counterColumn)
    val mutation = new thrift.Mutation
    mutation.setColumn_or_supercolumn(cosc)
  }

  private[this] def deleteMutation(key: Key, columnNames: JSet[Name]): thrift.Mutation = {
    val pred = new thrift.SlicePredicate
    pred.setColumn_names(cf.nameCodec.encodeSet(columnNames))

    val deletion = new thrift.Deletion
    deletion.setPredicate(pred)

    val mutation = new thrift.Mutation
    mutation.setDeletion(deletion)
  }
}
