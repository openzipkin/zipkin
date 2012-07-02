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

import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{FuturePool, Future}
import com.twitter.zipkin.config.ScribeZipkinCollectorConfig
import com.twitter.zipkin.gen
import org.apache.zookeeper.KeeperException

/**
 * This class implements the log method from the Scribe Thrift interface.
 */
class ScribeCollectorService(config: ScribeZipkinCollectorConfig, val writeQueue: WriteQueue[Seq[_ <: String]], categories: Set[String])
  extends gen.ZipkinCollector.FutureIface with CollectorService {
  private val log = Logger.get

  private var zkNodes: Seq[ResilientZKNode] = Seq.empty

  val futurePool = FuturePool.defaultPool

  val TryLater = Future(gen.ResultCode.TryLater)
  val Ok = Future(gen.ResultCode.Ok)

  override def start() {
    /* Register a node in ZooKeeper for Scribe to pick up */
    val serverSet = new ServerSetImpl(config.zkClient, config.zkServerSetPath)
    val cluster = new ZookeeperServerSetCluster(serverSet)
    zkNodes = config.zkScribePaths.map {
      path =>
        new ResilientZKNode(path, config.serverAddr.getHostName + ":" + config.serverAddr.getPort,
          config.zkClient, config.timer, config.statsReceiver)
    }.toSeq
    zkNodes foreach (_.register())
    cluster.join(config.serverAddr)

    super.start()
  }

  override def shutdown() {
    try {
      zkNodes foreach (_.unregister())
    } catch {
      case e: KeeperException => log.error("Could not unregister scribe zk node. Will continue shut down anyway", e)
    }

    super.shutdown()
  }

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
        if (!categories.contains(entry.category.toLowerCase())) {
          Stats.incr("collector.invalid_category")
          None
        } else {
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

  def storeTopAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    Stats.incr("collector.storeTopAnnotations")
    log.info("storeTopAnnotations: " + serviceName + "; " + annotations)

    Stats.timeFutureMillis("collector.storeTopAnnotations") {
      config.aggregates.storeTopAnnotations(serviceName, annotations)
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
      config.aggregates.storeTopKeyValueAnnotations(serviceName, annotations)
    } rescue {
      case e: Exception =>
        log.error(e, "storeTopKeyValueAnnotations failed")
        Stats.incr("collector.storeTopKeyValueAnnotations")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  @throws(classOf[gen.AdjustableRateException])
  def setSampleRate(sampleRate: Double): Future[Unit] = {
    Stats.incr("collector.set_sample_rate")
    log.info("setSampleRate: " + sampleRate)

    Stats.timeFutureMillis("collector.setSampleRate") {
      futurePool(config.sampleRateConfig.set(sampleRate))
    } rescue {
      case e: Exception =>
        log.error(e, "setSampleRate failed")
        Stats.incr("collector.set_sample_rate_exception")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  @throws(classOf[gen.AdjustableRateException])
  def getSampleRate(): Future[Double] = {
    Stats.incr("collector.get_sample_rate")
    log.debug("getSampleRate")

    Stats.timeFutureMillis("collector.getSampleRate") {
      futurePool(config.sampleRateConfig.get)
    } rescue {
      case e: Exception =>
        log.error(e, "getSampleRate failed")
        Stats.incr("collector.get_sample_rate_exception")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  @throws(classOf[gen.AdjustableRateException])
  def setStorageRequestRate(storageRequestRate: Double): Future[Unit] = {
    Stats.incr("collector.set_storage_request_rate")
    log.info("setStorageRequest: " + storageRequestRate)

    Stats.timeFutureMillis("collector.setStorageRequest") {
      futurePool(config.storageRequestRateConfig.set(storageRequestRate))
    } rescue {
      case e: Exception =>
        log.error(e, "setStorageRequest failed")
        Stats.incr("collector.set_storage_request_rate_exception")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }

  @throws(classOf[gen.AdjustableRateException])
  def getStorageRequestRate(): Future[Double] = {
    Stats.incr("collector.get_storage_request_rate")
    log.debug("getStorageRequestRate")

    Stats.timeFutureMillis("collector.getStorageReqestRate") {
      futurePool(config.storageRequestRateConfig.get)
    } rescue {
      case e: Exception =>
        log.error(e, "getStorageRequestRate failed")
        Stats.incr("collector.get_storage_request_rate_exception")
        Future.exception(gen.AdjustableRateException(e.toString))
    }
  }
}
