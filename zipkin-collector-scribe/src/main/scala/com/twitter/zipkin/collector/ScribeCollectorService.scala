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
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.Store

/**
 * This class implements the log method from the Scribe Thrift interface.
 */
class ScribeCollectorService(
  val writeQueue: WriteQueue[Seq[_ <: String]],
  stores: Seq[Store],
  categories: Set[String]
) extends gen.ZipkinCollector.FutureIface with CollectorService {
  private val log = Logger.get

  val futurePool = FuturePool.unboundedPool

  val TryLater = Future(gen.ResultCode.TryLater)
  val Ok = Future(gen.ResultCode.Ok)

  /**
   * Accept lists of LogEntries.
   */
  override def log(logEntries: Seq[gen.LogEntry]): Future[gen.ResultCode] = {
    Stats.incr("collector.log")

    if (!running) {
      log.warning("Server not running, pushing back")
      return TryLater
    }

    Stats.addMetric("scribe_size", logEntries.map(_.message.size).sum)

    if (logEntries.isEmpty) {
      Stats.incr("collector.empty_logentry")
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

  def storeDependencies(serviceName: String, endpoints:Seq[String]): Future[Unit] = {
    Stats.incr("collector.storeTopAnnotations")
    log.info("storeDependencies: " + serviceName + "; " + endpoints)

    Stats.timeFutureMillis("collector.storeDependencies") {
      Future.join {
        stores map { _.aggregates.storeDependencies(serviceName, endpoints) }
      }
    } rescue {
      case e: Exception =>
        log.error(e, "storeDependencies failed")
        Stats.incr("collector.storeDependencies")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  def storeTopAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    Stats.incr("collector.storeTopAnnotations")
    log.info("storeTopAnnotations: " + serviceName + "; " + annotations)

    Stats.timeFutureMillis("collector.storeTopAnnotations") {
      Future.join {
        stores map { _.aggregates.storeTopAnnotations(serviceName, annotations) }
      }
    } rescue {
      case e: Exception =>
        log.error(e, "storeTopAnnotations failed")
        Stats.incr("collector.storeTopAnnotations")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  def storeTopKeyValueAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    Stats.incr("collector.storeTopKeyValueAnnotations")
    log.info("storeTopKeyValueAnnotations: " + serviceName + ";" + annotations)

    Stats.timeFutureMillis("collector.storeTopKeyValueAnnotations") {
      Future.join {
        stores map { _.aggregates.storeTopKeyValueAnnotations(serviceName, annotations) }
      }
    } rescue {
      case e: Exception =>
        log.error(e, "storeTopKeyValueAnnotations failed")
        Stats.incr("collector.storeTopKeyValueAnnotations")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }
}
