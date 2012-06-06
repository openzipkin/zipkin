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
import org.specs.mock.{JMocker, ClassMocker}
import org.specs.Specification
import scala.collection.JavaConverters._

class BucketedColumnFamilySpec extends Specification with JMocker with ClassMocker {

  "BucketedColumnFamily" should {
    val numBuckets = 10

    val key = "some_key"
    val col1 = Column(1L, 2L)
    val col2 = Column(3L, 4L)
    val col3 = Column(5L, 6L)

    val colMap = Map(col1.name -> col1, col2.name -> col2, col3.name -> col3).asJava

    val start = Some(6L)
    val end = None
    val count = 3

    "insert and get row" in {
      val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
      val bcf = new StringBucketedColumnFamily(cf, numBuckets)

      val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}

      expect {
        3.of(cf).insert(any[ByteBuffer], any[Column[Long, Long]])

        1.of(cf).multigetRows(keys.toSet.asJava, None, None, Order.Normal, Int.MaxValue) willReturn
          Future {
            Map (
              keys(0) -> Map(col1.name -> col1).asJava,
              keys(1) -> Map(col2.name -> col2).asJava,
              keys(2) -> Map(col3.name -> col3).asJava
            ).asJava
          }
      }

      bcf.insert(key, col1)
      bcf.insert(key, col2)
      bcf.insert(key, col3)

      val rowMap = bcf.getRow(key)
      rowMap() mustEqual colMap
    }

    "insert and get row slice" in {
      val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
      val bcf = new StringBucketedColumnFamily(cf, numBuckets)

      val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}

      expect {
        3.of(cf).insert(any[ByteBuffer], any[Column[Long, Long]])

        Seq(Order.Normal, Order.Reversed) foreach { o =>
          1.of(cf).multigetRows(keys.toSet.asJava, start, end, o, count) willReturn
            Future {
              Map (
                keys(0) -> Map(col1.name -> col1).asJava,
                keys(1) -> Map(col2.name -> col2).asJava,
                keys(2) -> Map(col3.name -> col3).asJava
              ).asJava
            }
        }
      }

      bcf.insert(key, col1)
      bcf.insert(key, col2)
      bcf.insert(key, col3)

      val rowNormal = bcf.getRowSlice(key, start, end, count, Order.Normal)()
      val rowReversed = bcf.getRowSlice(key, start, end, count, Order.Reversed)()

      rowNormal mustEqual List(col1, col2, col3)
      rowReversed mustEqual List(col3, col2, col1)
    }

    "only get limited number of entries" in {
      val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
      val bcf = new StringBucketedColumnFamily(cf, numBuckets)

      val count = 2
      val keys = (0 until numBuckets) map {bcf.makeBucketedKey(key, _)}

      expect {
        3.of(cf).insert(any[ByteBuffer], any[Column[Long, Long]])

        Seq(Order.Normal, Order.Reversed) foreach { o =>
          1.of(cf).multigetRows(keys.toSet.asJava, start, end, o, count) willReturn
            Future {
              Map (
                keys(0) -> Map(col1.name -> col1).asJava,
                keys(1) -> Map(col2.name -> col2).asJava,
                keys(2) -> Map(col3.name -> col3).asJava
              ).asJava
            }
        }
      }

      bcf.insert(key, col1)
      bcf.insert(key, col2)
      bcf.insert(key, col3)

      val rowNormal = bcf.getRowSlice(key, start, end, count, Order.Normal)()
      val rowReversed = bcf.getRowSlice(key, start, end, count, Order.Reversed)()

      rowNormal mustEqual List(col1, col2)
      rowReversed mustEqual List(col3, col2)
    }

    "roll over buckets correctly" in {
      val cf = mock[ColumnFamily[ByteBuffer, Long, Long]]
      val bcf = new StringBucketedColumnFamily(cf, numBuckets)

      expect {
        (0 until 11) foreach { i: Int =>
          1.of(cf).insert(bcf.makeBucketedKey(key, i % numBuckets), col1)
        }
      }

      (0 until 11) foreach { i: Int =>
        bcf.insert(key, col1)
      }
    }
  }
}
