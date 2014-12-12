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

import com.twitter.cassie.util.ByteBufferUtil
import com.twitter.util.{ Future, Promise }
import java.util.{ List => JList }
import org.apache.cassandra.finagle.thrift
import scala.collection.JavaConversions._
import scala.collection.mutable.Buffer

/**
 * Given a column family, a key range, a batch size, a slice predicate,
 * iterates through slices of each matching row until a cycle is detected
 * (e.g., Cassandra returns the last slice a second time) or until an empty
 * slice is returned (e.g., no more slices).
 * Provides a sequence of (row key, columns).
 *
 * EXAMPLE:
 * val cluster = new Cluster("127.0.0.1").keyspace("foo")
 *   .connect().columnFamily("bar", Utf8Codec, Utf8Codec, Utf8Codec)
 * val finished = cf.rowsIteratee(100).foreach { case(key, columns) =>
 *   println(key) //this function is executed async for each row
 *   println(cols)
 * }
 * finished() //this is a Future[Unit]. wait on it to know when the iteration is done
 */

trait RowsIteratee[Key, Name, Value] {
  def foreach(f: (Key, JList[Column[Name, Value]]) => Unit): Future[Unit] = {
    val p = new Promise[Unit]
    next map (_.visit(p, f)) handle { case e => p.setException(e) }
    p
  }
  def map[A](f: (Key, JList[Column[Name, Value]]) => A): Future[Seq[A]] = {
    val buffer = Buffer.empty[A]
    foreach { case(key, columns) =>
      buffer.append(f(key, columns))
    }.map { _ => buffer }
  }
  def hasNext(): Boolean
  def next(): Future[RowsIteratee[Key, Name, Value]]
  def visit(p: Promise[Unit], f: (Key, JList[Column[Name, Value]]) => Unit): Unit
}

object RowsIteratee {
  def apply[Key, Name, Value](cf: ColumnFamily[Key, Name, Value], batchSize: Int, pred: thrift.SlicePredicate) = {
    new InitialRowsIteratee(cf, batchSize, pred)
  }

  def apply[Key, Name, Value](cf: ColumnFamily[Key, Name, Value], start: Key, end: Key, batchSize: Int, pred: thrift.SlicePredicate) = {
    new InitialRowsIteratee(cf, start, end, batchSize, pred)
  }
}

private[cassie] class InitialRowsIteratee[Key, Name, Value](val cf: ColumnFamily[Key, Name, Value],
  val start: Key,
  val end: Key,
  val batchSize: Int,
  val predicate: thrift.SlicePredicate) extends RowsIteratee[Key, Name, Value] {

  def this(cf: ColumnFamily[Key, Name, Value], batchSize: Int, pred: thrift.SlicePredicate) = {
    this(cf, cf.keyCodec.decode(ByteBufferUtil.EMPTY), cf.keyCodec.decode(ByteBufferUtil.EMPTY),
      batchSize, pred)
  }

  def visit(p: Promise[Unit], f: (Key, JList[Column[Name, Value]]) => Unit): Unit = {
    throw new UnsupportedOperationException("no need to visit the initial Iteratee")
  }

  override def hasNext() = true

  def next(): Future[RowsIteratee[Key, Name, Value]] = {
    cf.getRangeSlice(start, end, batchSize, predicate) map { buf =>
      // the last found key, or the end key if the slice was empty
      buf.lastOption match {
        case None => new FinalRowsIteratee(buf)
        case Some(row) => new SubsequentRowsIteratee(cf, row._1, end, batchSize, predicate, buf)
      }
    }
  }
}

private[cassie] class SubsequentRowsIteratee[Key, Name, Value](
  cf: ColumnFamily[Key, Name, Value],
  start: Key,
  end: Key,
  batchSize: Int,
  predicate: thrift.SlicePredicate,
  buffer: JList[(Key, JList[Column[Name, Value]])]) extends RowsIteratee[Key, Name, Value] {
  override def hasNext = true

  def visit(p: Promise[Unit], f: (Key, JList[Column[Name, Value]]) => Unit): Unit = {
    for ((key, columns) <- buffer) {
      f(key, columns)
    }
    next map { n =>
      n.visit(p, f)
    } handle { case e => p.setException(e) }
  }

  def next() = {
    cf.getRangeSlice(start, end, batchSize + 1, predicate).map { buf =>
      val skipped = buf.subList(1, buf.length)
      skipped.lastOption match {
        case None => new FinalRowsIteratee(skipped)
        case Some(r) => new SubsequentRowsIteratee(cf, r._1, end, batchSize, predicate, skipped)
      }
    }
  }
}

private[cassie] class FinalRowsIteratee[Key, Name, Value](buffer: JList[(Key, JList[Column[Name, Value]])]) extends RowsIteratee[Key, Name, Value] {
  override def hasNext = false
  def next = Future.exception(new UnsupportedOperationException("No more results."))
  def visit(p: Promise[Unit], f: (Key, JList[Column[Name, Value]]) => Unit) = {
    for ((key, columns) <- buffer) {
      f(key, columns)
    }
    p.setValue(Unit)
  }
}
