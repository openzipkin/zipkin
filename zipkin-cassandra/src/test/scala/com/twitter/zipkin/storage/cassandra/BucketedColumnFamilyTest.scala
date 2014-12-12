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

import com.twitter.cassie.{Order, Column, ColumnFamily}
import com.twitter.util.Future
import java.nio.ByteBuffer
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class BucketedColumnFamilyTest extends FunSuite with MockitoSugar {
  val numBuckets = 10

  val key = "some_key"
  val col1 = Column(1L, 2L)
  val col2 = Column(3L, 4L)
  val col3 = Column(5L, 6L)

  val colMap = Map(col1.name -> col1, col2.name -> col2, col3.name -> col3).asJava

  val start = Some(6L)
  val end = None
  val count = 3

  test("insert and get row") {
    val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
    val bcf = new StringBucketedColumnFamily(cf, numBuckets)

    val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}

    when(cf.multigetRows(keys.toSet.asJava, None, None, Order.Normal, Int.MaxValue)).thenReturn(Future {
      Map (
        keys(0) -> Map(col1.name -> col1).asJava,
        keys(1) -> Map(col2.name -> col2).asJava,
        keys(2) -> Map(col3.name -> col3).asJava
      ).asJava
    })

    bcf.insert(key, col1)
    bcf.insert(key, col2)
    bcf.insert(key, col3)

    val rowMap = bcf.getRow(key)
    assert(rowMap() === colMap)
    verify(cf, times(3)).insert(any[ByteBuffer], any[Column[Long, Long]])
    verify(cf, times(1)).multigetRows(keys.toSet.asJava, None, None, Order.Normal, Int.MaxValue)
  }

  test("insert and get row slice") {
    val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
    val bcf = new StringBucketedColumnFamily(cf, numBuckets)

    val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}
    Seq(Order.Normal, Order.Reversed) foreach { o =>
      when(cf.multigetRows(keys.toSet.asJava, start, end, o, count)).thenReturn(Future {
        Map (
          keys(0) -> Map(col1.name -> col1).asJava,
          keys(1) -> Map(col2.name -> col2).asJava,
          keys(2) -> Map(col3.name -> col3).asJava
        ).asJava
      })
    }

    bcf.insert(key, col1)
    bcf.insert(key, col2)
    bcf.insert(key, col3)

    val rowNormal = bcf.getRowSlice(key, start, end, count, Order.Normal)()
    val rowReversed = bcf.getRowSlice(key, start, end, count, Order.Reversed)()

    assert(rowNormal === List(col1, col2, col3))
    assert(rowReversed === List(col3, col2, col1))
    verify(cf, times(3)).insert(any[ByteBuffer], any[Column[Long, Long]])
  }

  test("only get limited number of entries") {
    val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
    val bcf = new StringBucketedColumnFamily(cf, numBuckets)

    val count = 2
    val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}

    Seq(Order.Normal, Order.Reversed) foreach { o =>
      when(cf.multigetRows(keys.toSet.asJava, start, end, o, count)).thenReturn(Future {
        Map (
          keys(0) -> Map(col1.name -> col1).asJava,
          keys(1) -> Map(col2.name -> col2).asJava,
          keys(2) -> Map(col3.name -> col3).asJava
        ).asJava
      })
    }

    bcf.insert(key, col1)
    bcf.insert(key, col2)
    bcf.insert(key, col3)

    val rowNormal = bcf.getRowSlice(key, start, end, count, Order.Normal)()
    val rowReversed = bcf.getRowSlice(key, start, end, count, Order.Reversed)()

    assert(rowNormal === List(col1, col2))
    assert(rowReversed === List(col3, col2))
    verify(cf, times(3)).insert(any[ByteBuffer], any[Column[Long, Long]])

  }

  test("roll over buckets correctly") {
    val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
    val bcf = new StringBucketedColumnFamily(cf, numBuckets)

    (0 until numBuckets) foreach { i: Int =>
      bcf.insert(key, col1)
      verify(cf, times(1)).insert(bcf.makeBucketedKey(key, i % numBuckets), col1)
    }
  }
}
