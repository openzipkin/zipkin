/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.storage.cassandra

import scala.collection.JavaConverters._
import java.nio.ByteBuffer
import java.util.{Map => JMap}

import com.twitter.cassie._
import com.twitter.cassie.codecs.{Codec, ByteArrayCodec}
import com.twitter.util.Future

object BucketedColumnFamily {

  def apply[Name, Value](
    keyspace: Keyspace,
    columnFamilyName: String,
    nameCodec: Codec[Name],
    valueCodec: Codec[Value],
    writeConsistency: WriteConsistency,
    readConsistency: ReadConsistency
  ) = {
    keyspace.columnFamily(
      columnFamilyName,
      ByteArrayCodec,
      nameCodec,
      valueCodec
    ).consistency(writeConsistency).consistency(readConsistency)
  }
}

/**
 * BucketedColumnFamily
 * Maps a particular row key to _numBuckets_ "sub-keys" to spread out load
 * for hot rows. Currently we simply increment a BoundedCounter to assign
 * the Column to a particular sub-key. Keys must be Strings
 *
 * Example:
 *   Row key: "key"
 *   numBuckets: 3
 *
 *   Sub-keys:
 *     "key_0"
 *     "key_1"
 *     "key_2"
 *
 * When getting a row slice, we run the get on all the sub-keys, merge
 * them together, and sort the resulting sequence.
 *
 * NOTE: It is possible with BucketedColumnFamily, as with a
 * ColumnFamily to override existing data if there is a column key
 * collision, however the risk is somewhat smaller due to the partitioning.
 *
 *
 * @param columnFamily: the ColumnFamily we're wrapping
 * @param numBuckets  : number of buckets to use
 * @tparam Name       : type of the column key
 * @tparam Value      : type of the value
 */
abstract class BucketedColumnFamily[Key, Name <% Ordered[Name], Value](
  val columnFamily: ColumnFamily[ByteBuffer, Name, Value],
  numBuckets: Int
) extends ColumnFamily[Key, Name, Value] {

  private[this] val counter = new BoundedCounter(numBuckets)

  override def batch(): BatchMutationBuilder[Key, Name, Value] = new BucketedBatchMutationBuilder(this)

  override def insert(key: Key, column: Column[Name, Value]): Future[Void] = {
    val newKey = makeNextBucketedKey(key)
    columnFamily.insert(newKey, column)
  }

  /**
   * Returns the combined row from all buckets.
   *
   * NOTE: Column key collisions override each other. For current use cases,
   * this is not a problem (ex: ServiceName)
   *
   */
  override def getRow(key: Key): Future[JMap[Name, Column[Name, Value]]] = {
    columnFamily.multigetRows(bucketedKeys(key).toSet.asJava, None, None, Order.Normal, Int.MaxValue) map {
      _.values().asScala.toSeq.map {
        _.asScala.toMap
      }.reduceLeft(_ ++ _).asJava
    }
  }

  /**
   * Returns the combined row slice from all buckets.
   */
  override def getRowSlice(
    key: Key,
    start: Option[Name],
    end: Option[Name],
    count: Int,
    order: Order
  ): Future[Seq[Column[Name, Value]]] = {

    columnFamily.multigetRows(bucketedKeys(key).toSet.asJava, start, end, order, count) map {
      _.values().asScala.toSeq.map {
        _.values().asScala.toSeq
      }.flatten.sortWith { (a, b) =>
        if (order.normal) {
          a.name.compare(b.name) < 0
        } else {
          a.name.compare(b.name) > 0
        }
      }.slice(0, count)
    }
  }

  protected def convertKey(key: Key): ByteBuffer

  def makeBucketedKey(key: Key, bucketNum: Int) = {
    val keyBytes = convertKey(key)
    val buf = ByteBuffer.allocate(keyBytes.capacity + 4)
    buf.put(keyBytes)
    buf.putInt(bucketNum)
    buf.rewind()
    buf
  }

  def makeNextBucketedKey(key: Key) = {
    makeBucketedKey(key, nextBucket())
  }

  private def nextBucket() = counter.next()

  private def bucketedKeys(key: Key): Seq[ByteBuffer] = (0 until numBuckets) map {
    makeBucketedKey(key, _)
  }

  private class BoundedCounter(max: Int) {
    private[this] var c = 0

    def next() = synchronized {
      val r = c
      c += 1
      c %= max
      r
    }
  }
}

class StringBucketedColumnFamily[Name <% Ordered[Name], Value] (
  columnFamily: ColumnFamily[ByteBuffer, Name, Value],
  numBuckets: Int
) extends BucketedColumnFamily[String, Name, Value] (
  columnFamily,
  numBuckets
) {

  def convertKey(key: String): ByteBuffer = {
    ByteBuffer.wrap(key.getBytes)
  }
}

class ByteBufferBucketedColumnFamily[Name <% Ordered[Name], Value] (
  columnFamily: ColumnFamily[ByteBuffer, Name, Value],
  numBuckets: Int
) extends BucketedColumnFamily[ByteBuffer, Name, Value] (
  columnFamily,
  numBuckets
) {

  def convertKey(key: ByteBuffer): ByteBuffer = {
    key.duplicate()
  }
}

class BucketedBatchMutationBuilder[Key, Name, Value] (
  bcf: BucketedColumnFamily[Key, Name, Value]
) extends BatchMutationBuilder[Key, Name, Value] (null) {

  val bmb: BatchMutationBuilder[ByteBuffer, Name, Value] = new BatchMutationBuilder(bcf.columnFamily)

  override def insert(key: Key, column: Column[Name, Value]): This = synchronized {
    bmb.insert(bcf.makeNextBucketedKey(key), column)
    this
  }

  override def execute() = {
    bmb.execute()
  }
}
