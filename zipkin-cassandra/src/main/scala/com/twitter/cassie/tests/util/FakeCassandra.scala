// Copyright 2012 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.tests.util

import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.{ArrayList => JArrayList, TreeMap => JTreeMap, List => JList, TreeSet => JTreeSet, Map => JMap, Comparator}
import org.apache.cassandra.finagle.thrift.ColumnOrSuperColumn
import org.apache.cassandra.finagle.thrift.ColumnParent
import org.apache.cassandra.finagle.thrift.ConsistencyLevel
import org.apache.cassandra.finagle.thrift.KeyRange
import org.apache.cassandra.finagle.thrift.KeySlice
import org.apache.cassandra.finagle.thrift.SlicePredicate
import org.apache.cassandra.finagle.thrift._
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.server.TThreadPoolServer
import org.apache.thrift.transport.{ TServerSocket, TFramedTransport }
import scala.collection.JavaConversions._
import scala.math.min

object FakeCassandra {
  class ServerThread(cassandra: Cassandra.Iface) extends Thread {
    setDaemon(true)
    val serverSocket = new ServerSocket(0) // so we can extract port if picked by server socket
    val serverTransport = new TServerSocket(serverSocket)
    val protFactory = new TBinaryProtocol.Factory(true, true)
    val transportFactory = new TFramedTransport.Factory()
    val processor = new Cassandra.Processor(cassandra)
    val server = new TThreadPoolServer(processor, serverTransport, transportFactory, protFactory)
    val latch = new CountDownLatch(1)

    val port = serverSocket.getLocalPort

    override def run() {
      latch.countDown()
      server.serve()
    }
  }
}

/**
 * This is a thrift-service that will do real operations on an in-memory data structure.
 * You don't have to create keyspaces or column families; this happens implicitly.  We
 * support a limited and expanding subset of the cassandra api.
 *
 * Note, because Finagle often is the client to this, the data structures must be thread safe.
 * This is implemented in the simplest way possible: serializing all access.
 */
class FakeCassandra extends Cassandra.Iface {
  // Taken from cassandra ByteBufferUtil#compareUnsigned
  // Questionable style because it's as straight a port as possible
  // replace this with a straight copy of the java or split the fake out into a subproject and depend on cassandra
  val comparator = new Comparator[ByteBuffer] {
    def compare(o1: ByteBuffer, o2: ByteBuffer): Int = {
      // o1.rewind
      // o2.rewind
      if (o1 eq null) {
        if (o2 eq null) return 0
        else return -1
      } else if (o2 eq null) {
        return 1
      }

      val minLength = min(o1.remaining(), o2.remaining())
      var x = 0
      var i = o1.position()
      var j = o2.position()
      while (x < minLength) {
        if (o1.get(i) != o2.get(j)) {

          // compare non-equal bytes as unsigned
          return if ((o1.get(i) & 0xFF) < (o2.get(j) & 0xFF)) -1 else 1
        }

        x += 1
        i += 1
        j += 1
      }

      if (o1.remaining() == o2.remaining()) 0 else (if (o1.remaining() < o2.remaining()) -1 else 1)
    }
  }

  @volatile
  var thread: FakeCassandra.ServerThread = null
  val currentKeyspace = new ThreadLocal[String] {
    override def initialValue = "default"
  }

  def port: Option[Int] = if (thread != null) Some(thread.port) else None

  //                     keyspace        CF              row         column
  val data = new JTreeMap[String, JTreeMap[String, JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]]]]

  def start() {
    thread = new FakeCassandra.ServerThread(this)
    thread.start()
    thread.latch.await()
  }

  private def getColumnFamily(cp: ColumnParent): JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]] =
    getColumnFamily(cp.getColumn_family)
  private def getColumnFamily(cp: ColumnPath): JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]] = getColumnFamily(cp.getColumn_family)
  private def getColumnFamily(name: String): JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]] = synchronized {
    var keyspace = data.get(currentKeyspace.get)
    if (keyspace == null) {
      keyspace = new JTreeMap[String, JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]]]
      data.put(currentKeyspace.get, keyspace)
    }
    var cf = keyspace.get(name)
    if (cf == null) {
      cf = new JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]](comparator)
      keyspace.put(name, cf)
    }
    cf
  }

  def stop() {
    thread.server.stop()
    thread.serverSocket.close()
    reset()
  }

  def reset() {
    synchronized { data.clear() }
  }

  def set_keyspace(keyspace: String) { currentKeyspace.set(keyspace) }

  def insert(key: ByteBuffer, column_parent: ColumnParent, column: Column, consistency_level: ConsistencyLevel) = synchronized {
    val cf = getColumnFamily(column_parent)
    var row = cf.get(key)
    if (row == null) {
      row = new JTreeMap[ByteBuffer, ColumnOrSuperColumn](comparator)
      cf.put(key, row)
    }

    if (column_parent.isSetSuper_column) {
      val super_column_name = column_parent.BufferForSuper_column
      var cosc = row.get(super_column_name)
      if (cosc == null) {
        cosc = new ColumnOrSuperColumn().setSuper_column(new SuperColumn(super_column_name, new JArrayList()))
        row.put(super_column_name, cosc)
      }
      val sc = cosc.getSuper_column
      var existing = sc.getColumns.find(_.getName.sameElements(column.getName))
      existing match {
        case Some(c) => {
          if (column.timestamp > c.timestamp) {
            c.setValue(column.getValue)
          }
        }
        case None => sc.getColumns.add(column)
      }
    } else {
      val existing = row.get(column.BufferForName)
      if (existing != null) {
        if (column.timestamp > existing.getColumn.timestamp) {
          row.put(column.BufferForName, new ColumnOrSuperColumn().setColumn(column))
        }
      } else {
        row.put(column.BufferForName, new ColumnOrSuperColumn().setColumn(column))
      }
    }
  }

  def multiget_slice(keys: JList[ByteBuffer], column_parent: ColumnParent,
    predicate: SlicePredicate, consistency_level: ConsistencyLevel) = synchronized {
    val map = new JTreeMap[ByteBuffer, JList[ColumnOrSuperColumn]]
    keys.foreach { key =>
      map.put(key, get_slice(key, column_parent, predicate, consistency_level))
    }
    map
  }

  def get_slice(key: ByteBuffer, column_parent: ColumnParent,
    predicate: SlicePredicate, consistency_level: ConsistencyLevel): JList[ColumnOrSuperColumn] =
    synchronized {
      get_slice(key, column_parent, predicate, consistency_level, System.currentTimeMillis, false)
    }

  def get_slice(key: ByteBuffer, column_parent: ColumnParent, predicate: SlicePredicate,
    consistency_level: ConsistencyLevel, asOf: Long, andDelete: Boolean): JList[ColumnOrSuperColumn] = synchronized {
    val cf = getColumnFamily(column_parent)
    val row = cf.get(key)
    if (row == null)
      return new JArrayList()
    var list = new JArrayList[ColumnOrSuperColumn]

    if (predicate.isSetSlice_range()) {
      val sr = predicate.getSlice_range
      if (sr.isSetStart && sr.getStart().length > 0 &&
        sr.isSetFinish && sr.getFinish().length > 0) {
        if (comparator.compare(sr.BufferForStart, sr.BufferForFinish) > 0) {
          list = new JArrayList(row.subMap(sr.BufferForFinish, sr.BufferForStart).values)
        } else {
          list = new JArrayList(row.subMap(sr.BufferForStart, sr.BufferForFinish).values)
        }
      } else if (sr.isSetStart() && sr.getStart.length > 0) {
        list = new JArrayList(row.tailMap(sr.BufferForStart).values)
      } else if (sr.isSetFinish() && sr.getFinish.length > 0) {
        list = new JArrayList(row.headMap(sr.BufferForFinish).values)
      } else {
        list = new JArrayList(row.values)
      }
    } else if (predicate.isSetColumn_names) {
      list = new JArrayList
      val names = predicate.getColumn_names
      for (name <- names) {
        val value = row.get(name)
        if (value != null) {
          list.add(value)
        }
      }
    }
    list
  }

  def batch_mutate(mutation_Map: JMap[ByteBuffer, JMap[String, JList[Mutation]]],
    consistency_level: ConsistencyLevel) = synchronized {
    for ((key, map) <- mutation_Map) {
      for ((cf, mutations) <- map) {
        val cp = new ColumnParent
        cp.setColumn_family(cf)
        for (mutation <- mutations) {
          if (mutation.isSetColumn_or_supercolumn) {
            val cosc = mutation.getColumn_or_supercolumn
            if (cosc.isSetColumn) {
              insert(key, cp, cosc.getColumn, ConsistencyLevel.ANY)
            } else {
              throw new UnsupportedOperationException("no supercolumn support")
            }
          }
          if (mutation.isSetDeletion) {
            val deletion = mutation.getDeletion
            for (name <- deletion.getPredicate.getColumn_names) {
              remove(key, (new ColumnPath).setColumn_family(cf).setColumn(name), deletion.getTimestamp, consistency_level)
            }
          }
        }
      }
    }
  }

  def login(auth_request: AuthenticationRequest) { throw new UnsupportedOperationException }

  def get(key: ByteBuffer, column_path: ColumnPath, consistency_level: ConsistencyLevel) =
    throw new UnsupportedOperationException

  def get_count(key: ByteBuffer, column_parent: ColumnParent, predicate: SlicePredicate,
    consistency_level: ConsistencyLevel) = synchronized {
    get_slice(key, column_parent, predicate, consistency_level).size
  }

  def multiget_count(keys: JList[ByteBuffer], column_parent: ColumnParent,
    predicate: SlicePredicate, consistency_level: ConsistencyLevel) = synchronized {
    val map = new JTreeMap[ByteBuffer, java.lang.Integer]
    multiget_slice(keys, column_parent, predicate, consistency_level).foreach { case (key, slice) =>
      map.put(key, slice.size)
    }
    map
  }

  // todo: note this doesn't support SliceRange predicates on SlicePredicate
  override def get_range_slices(
    column_parent: ColumnParent,
    predicate: SlicePredicate,
    range: KeyRange,
    consistency_level: ConsistencyLevel
  ): JList[KeySlice] = synchronized {
    if (predicate.isSetSlice_range()) {
      throw new IllegalArgumentException("slice range predicates are not supported")
    }

    val colNamesPredicate: Option[JTreeSet[ByteBuffer]] = if (!predicate.isSetColumn_names()) {
      None
    } else {
      val names = new JTreeSet[ByteBuffer](comparator)
      names.addAll(predicate.getColumn_names)
      Some(names)
    }

    // row id to map of column name to column value
    val cf: JTreeMap[ByteBuffer, JTreeMap[ByteBuffer, ColumnOrSuperColumn]] = getColumnFamily(column_parent)

    val keySlices: JList[KeySlice] = new JArrayList[KeySlice]

    // iterate through and just keep the keys that match the key range
    cf.foreach { case (rowId, columns) =>
      if ((range.start_key.remaining() == 0 || comparator.compare(rowId, range.start_key) >= 0)
         && (range.end_key.remaining() == 0 || comparator.compare(rowId, range.end_key) < 0))
      {
        val filtered = columns filter { case (k, v) =>
          colNamesPredicate match {
            case None =>
              true
            case Some(set) =>
              set.contains(k)
          }
        }

        if (filtered.size > 0) {
          val ks = new KeySlice(
            rowId,
            new JArrayList[ColumnOrSuperColumn](filtered.values))
          keySlices.add(ks)
        }
      }
    }
    keySlices
  }

  def get_indexed_slices(column_parent: ColumnParent, index_clause: IndexClause,
    column_predicate: SlicePredicate, consistency_level: ConsistencyLevel) =
    throw new UnsupportedOperationException

  def remove(key: ByteBuffer, column_path: ColumnPath, timestamp: Long,
    consistency_level: ConsistencyLevel) = synchronized {
    val cf = getColumnFamily(column_path)
    val row = cf.get(key)
    if (row != null) {
      (column_path.isSetSuper_column, column_path.isSetColumn) match {
        case (false, false) => cf.remove(key) // remove whole row
        case (false, true) => row.remove(column_path.BufferForColumn) // remove column
        case (true, false) => row.remove(column_path.BufferForSuper_column) // remove whole supercolumn
        case (true, true) =>
          // remove single column from supercolumn
          val cosc = row.get(column_path.BufferForSuper_column)
          if (cosc != null) {
            val sc = cosc.getSuper_column
            sc.setColumns(sc.getColumns.filter(_.getName.sameElements(column_path.getColumn)))
          }
      }
    }
  }

  /**
   * @param cfname name of the column family to truncate in the currentKeyspace.
   */
  override def truncate(cfname: String) {
    val cf = getColumnFamily(cfname)
    cf.clear()
  }

  def add(key: ByteBuffer, column_parent: ColumnParent, column: CounterColumn,
    consistency_level: ConsistencyLevel) = synchronized {
    val cf = getColumnFamily(column_parent)
    var row = cf.get(key)
    if (row == null) {
      row = new JTreeMap[ByteBuffer, ColumnOrSuperColumn](comparator)
      cf.put(key, row)
    }
    val col = row.get(column.BufferForName)
    if (col != null) {
      column.setValue(column.getValue + row.get(column.BufferForName).getCounter_column.getValue)
    }
    row.put(column.BufferForName, new ColumnOrSuperColumn().setCounter_column(column))
  }

  def remove_counter(key: ByteBuffer, path: ColumnPath,
    consistency_level: ConsistencyLevel) { throw new UnsupportedOperationException }

  def describe_schema_versions() = throw new UnsupportedOperationException
  def describe_keyspaces() = throw new UnsupportedOperationException
  def describe_cluster_name() = throw new UnsupportedOperationException
  def describe_version() = throw new UnsupportedOperationException
  def describe_ring(keyspace: String) = {
    val endpoints = new JArrayList[String]()
    endpoints.add("localhost")
    val tokenRanges = new JArrayList[TokenRange]()
    tokenRanges.add(new TokenRange("0", "0", endpoints))
    tokenRanges
  }
  def describe_partitioner() = throw new UnsupportedOperationException
  def describe_snitch() = throw new UnsupportedOperationException
  def describe_keyspace(keyspace: String) = throw new UnsupportedOperationException

  def describe_splits(cfName: String, start_token: String, end_token: String,
    keys_per_split: Int) = throw new UnsupportedOperationException

  def system_add_column_family(cf_def: CfDef) = throw new UnsupportedOperationException
  def system_drop_column_family(column_family: String) = throw new UnsupportedOperationException
  def system_add_keyspace(ks_def: KsDef) = throw new UnsupportedOperationException
  def system_drop_keyspace(keyspace: String) = throw new UnsupportedOperationException
  def system_update_keyspace(ks_def: KsDef) = throw new UnsupportedOperationException
  def system_update_column_family(cf_def: CfDef) = throw new UnsupportedOperationException
  def execute_cql_query(query: ByteBuffer, compression: Compression) = throw new UnsupportedOperationException
}
