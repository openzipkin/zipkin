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

import com.twitter.util.Future
import com.twitter.zipkin.storage.Aggregates
import scala.collection.JavaConverters._
import com.twitter.cassie.{Column, ColumnFamily}

/**
 * Cassandra backed aggregates store
 *
 * For each service, we store top annotations and top key value annotations
 * for use at query time.
 *
 * Top annotations are a sequence of the most popular time-based annotation strings
 * Top key value annotations are a sequence of the most popular _keys_ among key value annotations
 */
trait CassandraAggregates extends Aggregates with Cassandra {

  val topAnnotations: ColumnFamily[String, Long, String]

  val Delimiter: String = ":"

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
  private[cassandra] def storeAnnotations(key: String, annotations: Seq[String]): Future[Unit] = synchronized {
    val remove = topAnnotations.removeRow(key)
    val batch = topAnnotations.batch()
    annotations.zipWithIndex.foreach { case (annotation: String, index: Int) =>
      batch.insert(key, new Column[Long, String](index, annotation))
    }
    remove()
    Future.join(Seq(batch.execute()))
  }


  private[cassandra] def topAnnotationRowKey(serviceName: String) =
    serviceName + Delimiter + "annotation"

  private[cassandra] def topKeyValueRowKey(serviceName: String) =
    serviceName + Delimiter + "kv"
}
