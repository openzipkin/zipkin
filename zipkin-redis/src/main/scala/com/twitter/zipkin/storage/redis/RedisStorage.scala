/*
 * Copyright 2012 Tumblr Inc.
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

package com.twitter.zipkin.storage.redis

import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.Storage
import org.jboss.netty.buffer.ChannelBuffer

trait RedisStorage extends Storage {

  val database: Client

  override def close() = database.release()

  private[this] lazy val spanListMap = new RedisListMap(database, "full_span", ttl)

  val ttl: Option[Duration]

  override def storeSpan(span: Span): Future[Unit] = spanListMap.put(span.traceId, span).unit

  override def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] =
    spanListMap.setTTL(traceId, ttl).unit

  override def getTimeToLive(traceId: Long): Future[Duration] = spanListMap.getTTL(traceId) map (_.getOrElse(Duration.Top))

  override def getSpansByTraceId(traceId: Long) : Future[Seq[Span]] =
    fetchTraceById(traceId) map (_.get)

  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] =
    Future.collect(traceIds map (traceId => fetchTraceById(traceId))) map (_ flatten)

  override def getDataTimeToLive: Int = (ttl map (_.inSeconds)).getOrElse(Int.MaxValue)

  private[this] def fetchTraceById(traceId: Long): Future[Option[Seq[Span]]] =
    spanListMap.get(traceId) map (buf => optionify(sortedTrace(buf)))

  override def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] =
    Future.collect(traceIds map {id =>
      spanListMap.exists(id) map { exists =>
        if (exists) Some(id) else None
      }
    }) map (_.flatten.toSet)

  private[this] def optionify[A](spans: Seq[A]): Option[Seq[A]] = spans.headOption map (_ => spans)

  private[this] def firstTimestamp(span: Span): Long = span.firstAnnotation match {
    case Some(anno) => anno.timestamp
    case None => 0L
  }

  private[this] def sortedTrace(trace: Seq[ChannelBuffer]): Seq[Span] =
    (trace map deserializeSpan).sortBy[Long](firstTimestamp(_))(Ordering.Long.reverse)

}
