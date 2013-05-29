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

import com.twitter.cassie._
import com.twitter.util.{Time, Future, Return, Throw}
import com.twitter.conversions.time._
import com.twitter.zipkin.storage.Aggregates
import com.twitter.zipkin.conversions.thrift._
import scala.collection.JavaConverters._
import com.twitter.zipkin.gen
import scala.collection.immutable.NumericRange
import com.twitter.zipkin.common.Dependencies
import com.twitter.algebird.Monoid

/**
 * Cassandra backed aggregates store
 *
 * For each service, we store top annotations and top key value annotations
 * for use at query time.
 *
 * Top annotations are a sequence of the most popular time-based annotation strings
 * Top key value annotations are a sequence of the most popular _keys_ among key value annotations
 */
case class CassandraAggregates(
  keyspace: Keyspace,
  topAnnotations: ColumnFamily[String, Long, String],
  dependenciesCF: ColumnFamily[Long, Long, gen.Dependencies]
) extends Aggregates {

  def close() {
    keyspace.close()
  }

  val Delimiter: String = ":"

  /**
   * Get the top annotations for a service name
   */
  def getDependencies(startDate: Time, endDate: Option[Time]) : Future[Dependencies] = {

    // floor to nearest day in microseconds
    val realStart = startDate.floor(1.day).inMicroseconds
    val realEnd = endDate.getOrElse(startDate).floor(1.day).inMicroseconds

    val rows = new NumericRange.Inclusive[Long](realEnd, realStart, 1.days.inMicroseconds)

    val result: Future[Iterable[gen.Dependencies]] =
      dependenciesCF.multigetRows(rows.toSet.asJava, None, None, Order.Normal, Int.MaxValue)
        .map { rowMap =>
          rowMap.asScala.values.flatMap { columnMap =>
            columnMap.asScala.values.map { _.value }
          }
        }

    result.map { genList =>
      val depList = genList.map { _.toDependencies }
      Monoid.sum(depList) // reduce to one instance containing all values
    }
  }

  /**
   * Get the top annotations for a service name
   */
  def getTopAnnotations(serviceName: String): Future[Seq[String]] = {
    getAnnotations(topAnnotationRowKey(serviceName))
  }

  /**
   * Get the top key value annotation keys for a service name
   */
  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]] = {
    getAnnotations(topKeyValueRowKey(serviceName))
  }

  /**
   * Override the top annotations for a service
   */
  def storeTopAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    storeAnnotations(topAnnotationRowKey(serviceName), annotations)
  }

  /**
   * Override the top key value annotation keys for a service
   */
  def storeTopKeyValueAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    storeAnnotations(topKeyValueRowKey(serviceName), annotations)
  }

  private[cassandra] def getAnnotations(key: String): Future[Seq[String]] = {
    topAnnotations.getRow(key).map {
      _.values().asScala.map { _.value }.toSeq
    }
  }

  /** Synchronize these so we don't do concurrent writes from the same box */
  def storeDependencies(deps: Dependencies): Future[Unit] = {
    store[Long,gen.Dependencies](dependenciesCF, deps.startTime.floor(1.day).inMicroseconds, Seq(deps.toThrift))
  }

  /** Synchronize these so we don't do concurrent writes from the same box */
  private[cassandra] def storeAnnotations(key: String, annotations: Seq[String]): Future[Unit] =
    store[String,String](topAnnotations, key, annotations)

  private[cassandra] def store[Key,Val](cf: ColumnFamily[Key, Long, Val], key: Key, values: Seq[Val]): Future[Unit] = synchronized {
    val remove = cf.removeRow(key)
    val batch = cf.batch()
    values.zipWithIndex.foreach { case (value: Val, index: Int) =>
      batch.insert(key, new Column[Long, Val](index, value))
    }
    remove transform {
      case Return(r) => {
        batch.execute().unit
      }
      case Throw(e) => {
        Future.exception(e)
      }
    }
  }


  private[cassandra] def topAnnotationRowKey(serviceName: String) =
    serviceName + Delimiter + "annotation"

  private[cassandra] def topKeyValueRowKey(serviceName: String) =
    serviceName + Delimiter + "kv"
}
