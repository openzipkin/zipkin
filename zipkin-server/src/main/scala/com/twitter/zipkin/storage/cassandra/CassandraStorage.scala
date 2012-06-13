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

import com.twitter.zipkin.storage.Storage
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.gen
import com.twitter.ostrich.stats.Stats
import com.twitter.cassie.{Order, Column, ColumnFamily, Keyspace}
import com.twitter.zipkin.common.{Trace, Span}
import com.twitter.conversions.time._
import scala.collection.JavaConverters._
import com.twitter.zipkin.config.{CassandraConfig, CassandraStorageConfig}
import com.twitter.zipkin.adapter.ThriftAdapter

trait CassandraStorage extends Storage with Cassandra {

  val cassandraConfig: CassandraConfig

  val storageConfig: CassandraStorageConfig

  val keyspace: Keyspace

  val traces: ColumnFamily[Long, String, gen.Span]

  // storing the span in the traces cf
  private val CASSANDRA_STORE_SPAN = Stats.getCounter("cassandra_storespan")

  // read the trace
  private val CASSANDRA_GET_TRACE = Stats.getCounter("cassandra_gettrace")

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
    val traceCol = Column[String, gen.Span](createSpanColumnName(span), ThriftAdapter(span)).ttl(cassandraConfig.tracesTimeToLive)
    Future.join {
      Seq(traces.insert(traceKey, traceCol))
    }
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

    // convert to Future[Unit]. Sigh.
    Future.join(Seq(batch.execute()))
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
   * Fetches traces from the underlying storage. Note that there might be multiple
   * entries per span.
   */
  def getTraceById(traceId: Long): Future[Trace] = {
    getTracesByIds(Seq(traceId)).map {
      _.head
    }
  }

  def getTracesByIds(traceIds: Seq[Long]): Future[Seq[Trace]] = {
    CASSANDRA_GET_TRACE.incr
    Future.collect {
      traceIds.grouped(storageConfig.traceFetchBatchSize).toSeq.map { ids =>
        traces.multigetRows(ids.toSet.asJava, None, None, Order.Normal, TRACE_MAX_COLS).map { rowSet =>
          ids.flatMap { id =>
            val spans = rowSet.asScala(id).asScala.map {
              case (colName, col) => ThriftAdapter(col.value)
            }

            if (spans.isEmpty) {
              None
            } else if (spans.size >= TRACE_MAX_COLS) {
              log.error("Could not fetch the whole trace: " + id + " due to it being too big. Should not happen!")
              CASSANDRA_GET_TRACE_TOO_BIG.incr()
              None
            } else {
              Some(Trace(spans.toSeq).mergeSpans.sortedByTimestamp)
            }
          }
        }
      }
    }.map {
      _.flatten
    }
  }

  def getDataTimeToLive: Int = cassandraConfig.tracesTimeToLive.inSeconds

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
