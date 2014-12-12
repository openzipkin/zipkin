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
import java.nio.ByteBuffer
import java.util.Collections.{ singleton => singletonJSet }
import java.util.{ List => JList, Map => JMap, Set => JSet, ArrayList => JArrayList,HashMap => JHashMap}
import org.apache.cassandra.finagle.thrift
import scala.collection.mutable.ListBuffer

/**
 * A ColumnFamily-alike which batches mutations into a single API call.
 *
 */
class BatchMutationBuilder[Key, Name, Value](private[cassie] val cf: ColumnFamily[Key, Name, Value])
  extends BatchMutation {

  type This = BatchMutationBuilder[Key, Name, Value]

  def insert(key: Key, column: Column[Name, Value]): This = synchronized {
    val mutation = insertMutation(key, column)
    val encodedKey = cf.keyCodec.encode(key)
    putMutation(encodedKey, cf.name, mutation)
    this
  }

  def removeColumn(key: Key, columnName: Name): This =
    removeColumns(key, singletonJSet(columnName))

  def removeColumn(key: Key, columnName: Name, timestamp: Long): This =
    removeColumns(key, singletonJSet(columnName), timestamp)

  def removeColumns(key: Key, columns: JSet[Name]): This =
    removeColumns(key, columns, cf.clock.timestamp)

  def removeColumns(key: Key, columnNames: JSet[Name], timestamp: Long): This = synchronized {
    val mutation = deleteMutation(key, columnNames, timestamp)
    val encodedKey = cf.keyCodec.encode(key)

    putMutation(encodedKey, cf.name, mutation)
    this
  }

  /**
   * Submits the batch of operations, returning a Future[Void] to allow blocking for success.
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

  private[this] def insertMutation(key: Key, column: Column[Name, Value]): thrift.Mutation = {
    val cosc = new thrift.ColumnOrSuperColumn
    cosc.setColumn(
      Column.convert(
        cf.nameCodec,
        cf.valueCodec,
        cf.clock,
        column
      )
    )
    val mutation = new thrift.Mutation
    mutation.setColumn_or_supercolumn(cosc)
  }

  private[this] def deleteMutation(key: Key, columnNames: JSet[Name], timestamp: Long): thrift.Mutation = {
    val pred = new thrift.SlicePredicate
    pred.setColumn_names(cf.nameCodec.encodeSet(columnNames))

    val deletion = new thrift.Deletion()
    deletion.setTimestamp(timestamp)
    deletion.setPredicate(pred)

    val mutation = new thrift.Mutation
    mutation.setDeletion(deletion)
  }
}
