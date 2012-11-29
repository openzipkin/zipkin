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

import com.twitter.cassie.codecs.{Codec, Utf8Codec, LongCodec}
import com.twitter.cassie._
import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.Storage
import scala.collection.JavaConverters._

case class CassandraStorage(
  keyspace: Keyspace,
  columnFamily: String,
  writeConsistency: WriteConsistency,
  readConsistency: ReadConsistency,
  readBatchSize: Int,
  dataTimeToLive: Duration,
  spanCodec: Codec[gen.Span] = new SnappyCodec(new ScroogeThriftCodec[gen.Span](gen.Span))
) extends Storage {

  def close() {
    keyspace.close()
  }

  /**
   * Row key is the trace id.
   * Column name is the span identifier.
   * Value is a Thrift serialized Span.
   */
  val traces = keyspace.columnFamily(columnFamily, LongCodec, Utf8Codec, spanCodec)
    .consistency(writeConsistency)
    .consistency(readConsistency)

  // storing the span in the traces cf
  private val CASSANDRA_STORE_SPAN = Stats.getCounter("cassandra_storespan")

  // read the trace
  private val CASSANDRA_GET_TRACE = Stats.getCounter("cassandra_gettrace")

  // trace exist call
  private val CASSANDRA_TRACE_EXISTS = Stats.getCounter("cassandra_traceexists")

  // trace is too big!
  private val CASSANDRA_GET_TRACE_TOO_BIG = Stats.getCounter("cassandra_gettrace_too_big")

  private val WRITE_REQUEST_COUNTER = Stats.getCounter("cassandra.write_request_counter")

  // there's a bug somewhere that creates massive traces. if we try to
  // read them without a limit we run the risk of blowing up the memory used in
  // cassandra. so instead we limit it and won't return it. hacky.
  private val TRACE_MAX_COLS = 100000

  def storeSpan(span: Span): Future[Unit] = {
    CASSANDRA_STORE_SPAN.incr
    WRITE_REQUEST_COUNTER.incr()
    val traceKey = span.traceId
    val traceCol = Column[String, gen.Span](createSpanColumnName(span), span.toThrift).ttl(dataTimeToLive)
    traces.insert(traceKey, traceCol).unit
  }

  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
    val rowFuture = traces.getRow(traceId)
    val batch = traces.batch()

    // fetch each col for trace, change ttl and reinsert
    // note that we block here
    rowFuture().values().asScala.foreach { value =>
    // creating a new column in order to set timestamp to None
      val col = Column[String, gen.Span](value.name, value.value).ttl(ttl)
      batch.insert(traceId, col)
    }

    batch.execute().unit
  }

  def getTimeToLive(traceId: Long): Future[Duration] = {
    val rowFuture = traces.getRow(traceId)
    rowFuture map { rows =>
    // fetch the
      val minTtlSec = rows.values().asScala.foldLeft(Int.MaxValue)((ttl: Int, col: Column[String, gen.Span]) =>
        math.min(ttl, col.ttl.map(_.inSeconds).getOrElse(Int.MaxValue)))
      if (minTtlSec == Int.MaxValue) {
        throw new IllegalArgumentException("The trace " + traceId + " does not have any ttl set!")
      }
      minTtlSec.seconds
    }
  }

  /**
   * Finds traces that have been stored from a list of trace IDs
   *
   * @param traceIds a List of trace IDs
   * @return a Set of those trace IDs from the list which are stored
   */

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    CASSANDRA_TRACE_EXISTS.incr
    Future.collect {
      traceIds.grouped(readBatchSize).toSeq.map { ids =>
        traces.multigetRows(ids.toSet.asJava, None, None, Order.Normal, 1).map { rowSet =>
          ids.flatMap { id =>
            val spans = rowSet.asScala(id).asScala.map {
              case (colName, col) => col.value.toSpan
            }
            if (spans.isEmpty) {
              None
            } else {
              Some(spans.head.traceId)
            }
          }.toSet
        }
      }
    }.map {
      _.reduce { (left, right) => left ++ right }
    }
  }

  /**
   * Fetches traces from the underlying storage. Note that there might be multiple
   * entries per span.
   */
  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = {
    getSpansByTraceIds(Seq(traceId)).map {
      _.head
    }
  }

  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    CASSANDRA_GET_TRACE.incr
    Future.collect {
      traceIds.grouped(readBatchSize).toSeq.map { ids =>
        traces.multigetRows(ids.toSet.asJava, None, None, Order.Normal, TRACE_MAX_COLS).map { rowSet =>
          ids.flatMap { id =>
            val spans = rowSet.asScala(id).asScala.map {
              case (colName, col) => col.value.toSpan
            }

            spans.toSeq match {
              case Nil => {
                None
              }
              case s if s.length > TRACE_MAX_COLS => {
                CASSANDRA_GET_TRACE_TOO_BIG.incr()
                None
              }
              case s => {
                Some(s)
              }
            }
          }
        }
      }
    }.map {
      _.flatten
    }
  }

  def getDataTimeToLive: Int = dataTimeToLive.inSeconds

  /*
  * Helper methods
  * --------------
  */

  /**
   * One span will be logged by two different machines we want to store it in cassandra
   * without having to do a read first to do so we create a unique column name
   */
  private def createSpanColumnName(span: Span) : String = {
    // TODO make into a codec?
    span.id.toString + "_" + span.annotations.hashCode
  }
}
