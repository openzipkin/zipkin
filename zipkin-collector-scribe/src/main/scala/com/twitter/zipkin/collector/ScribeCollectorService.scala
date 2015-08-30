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
package com.twitter.zipkin.collector

import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{FuturePool, Future}
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.conversions.thrift._

/**
 * This class implements the log method from the Scribe Thrift interface.
 */
class ScribeCollectorService(
  val writeQueue: WriteQueue[Seq[_ <: String]],
  val stores: Seq[Store],
  categories: Set[String]
) extends thriftscala.ZipkinCollector.FutureIface with CollectorService {
  private val log = Logger.get

  val futurePool = FuturePool.unboundedPool

  val TryLater = Future(thriftscala.ResultCode.TryLater)
  val Ok = Future(thriftscala.ResultCode.Ok)

  /**
   * Accept lists of LogEntries.
   */
  override def log(logEntries: Seq[thriftscala.LogEntry]): Future[thriftscala.ResultCode] = {
    Stats.incr("collector.log")

    if (!running) {
      log.warning("Server not running, pushing back")
      return TryLater
    }

    Stats.addMetric("scribe_size", logEntries.map(_.message.size).sum)

    if (logEntries.isEmpty) {
      Stats.incr("collector.empty_lothriftscala.ry")
      return Ok
    }

    val scribeMessages = logEntries.flatMap {
      entry =>
        val category = entry.category.toLowerCase()
        if (!categories.contains(category)) {
          Stats.incr("collector.invalid_category")
          None
        } else {
          Stats.incr("category." + category)
          Some(entry.`message`)
        }
    }

    if (scribeMessages.isEmpty) {
      Ok
    } else if (writeQueue.add(scribeMessages)) {
      Stats.incr("collector.batches_added_to_queue")
      Stats.addMetric("collector.batch_size", scribeMessages.size)
      Ok
    } else {
      Stats.incr("collector.pushback")
      TryLater
    }
  }

  override def storeDependencies(dependencies: thriftscala.Dependencies): Future[Unit] = {

    Stats.timeFutureMillisLazy("collector.storeDependencies") {
      Future.join {
        stores map { _.aggregates.storeDependencies(dependencies.toDependencies) }
      }
    } rescue {
      case e: Exception =>
        log.error(e, "storeDependencies failed")
        Stats.incr("collector.storeDependencies")
        Future.exception(thriftscala.StoreAggregatesException(e.toString))
    }
  }
}
