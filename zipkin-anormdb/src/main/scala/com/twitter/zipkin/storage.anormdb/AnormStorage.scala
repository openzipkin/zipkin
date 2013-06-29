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
package com.twitter.zipkin.storage.anormdb

import com.twitter.zipkin.storage.Storage
import com.twitter.zipkin.common.Span
import com.twitter.util.{Duration, Future}
import anorm._
import anorm.SqlParser._
import com.twitter.zipkin.storage.anormdb.DB

/**
 * Retrieve and store span information.
 *
 * This is one of two places where Zipkin interacts directly with the database,
 * the other one being AnormIndex.
 *
 * NOTE: We're ignoring TTL for now since unlike Cassandra and Redis, SQL
 * databases don't have that built in and it shouldn't be a big deal for most
 * sites. Several methods in this class deal with TTL and we just assume that
 * all spans will live forever.
 */
case class AnormStorage extends Storage {
  // Database connection object
  private val conn = DB.getConnection()

  /**
   * Close the storage
   */
  def close() { conn.close() }

  /**
   * Store the span in the underlying storage for later retrieval.
   * @return a future for the operation
   */
  def storeSpan(span: Span): Future[Unit] = {
    // TODO What goes in the binary annotation annotation_key?
    // spans:
    //   span_id, parent_id, trace_id, span_name, debug, duration, created_ts
    // annotations:
    //   span_id, trace_id, span_name, service_name, annotation_key,
    //   annotation_value, annotation_type

    SQL(
      """INSERT INTO spans
        |  (span_id, parent_id, trace_id, span_name, debug, duration, created_ts)
        |VALUES
        |  ({span_id}, {parent_id}, {trace_id}, {span_name}, {debug}, {duration}, {created_ts})
      """.stripMargin)
      .on("span_id" -> span.id)
      .on("parent_id" -> span.parentId) // note: nullable, so this is an Option
      .on("trace_id" -> span.traceId)
      .on("span_name" -> span.name)
      .on("debug" -> span.debug)
      .on("duration" -> span.duration) // note: nullable, so this is an Option
      .on("end_ts" -> span.firstAnnotation.map(_.timestamp).head)
    .execute()

    // Services are part of annotations and annotations are many-to-one per span
    // Also, there are two types of annotations: normal and binary. Binary
    // annotations are normally passed around as arbitrary thrift-encoded
    // structures with a string key, whereas normal annotations have a specific
    // structure that includes a string value. Normal annotations are stored
    // with the value as the annotation_key and the object serialized as the
    // annotation_value. Binary annotations are stored with (TODO what?) as the
    // annotation_key and the serialized annotation as the annotation_value.
    span.annotations.foreach(a =>
      SQL(
        """INSERT INTO annotations
          |  (span_id, trace_id, span_name, service_name, annotation_key, annotation_value, annotation_type)
          |VALUES
          |  ({span_id}, {trace_id}, {span_name}, {service_name}, {annotation_key}, {annotation_value}, {annotation_type})
        """.stripMargin)
        .execute()
    )
    // TODO see the TODOs above
    span.binaryAnnotations.foreach(a =>
      SQL(
        """INSERT INTO annotations
          |  (span_id, trace_id, span_name, service_name, annotation_key, annotation_value, annotation_type)
          |VALUES
          |  ({span_id}, {trace_id}, {span_name}, {service_name}, {annotation_key}, {annotation_value}, {annotation_type})
        """.stripMargin)
        .execute()
    )
    Future.Unit
  }

  /**
   * Set the ttl of a trace. Used to store a particular trace longer than the
   * default. It must be oh so interesting!
   */
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = {
    Future.Unit
  }

  /**
   * Get the time to live for a specific trace.
   * If there are multiple ttl entries for one trace, pick the lowest one.
   */
  def getTimeToLive(traceId: Long): Future[Duration] = {
    Future(Duration.Top)
  }

  /**
   * Finds traces that have been stored from a list of trace IDs
   *
   * @param traceIds a List of trace IDs
   * @return a Set of those trace IDs from the list which are stored
   */
  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    Future(SQL(
      "SELECT trace_id FROM spans WHERE trace_id IN (%s)".format(traceIds.mkString(","))
    )().toSet.map(row => row[Long]("trace_id")))
  }

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    // TODO
    // SELECT * FROM spans WHERE trace_id IN (%s)
    // And then for each of those, get the annotations
    // Or we could do some sort of nasty join, then aggregate in Scala
  }
  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = {
    getSpansByTraceIds(Seq(traceId)).map {
      _.head
    }
  }

  /**
   * How long do we store the data before we delete it? In seconds.
   */
  def getDataTimeToLive: Int = {
    Int.MaxValue
  }

}
