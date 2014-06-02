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
package com.twitter.zipkin.collector.sampler.adaptive

import com.twitter.zipkin.collector.sampler.AdaptiveSampler
import com.twitter.logging.Logger
import com.twitter.util.Timer

/**
 * AdaptiveSampler implementation that uses ZooKeeper
 */
trait ZooKeeperAdaptiveSampler extends AdaptiveSampler {

  val log = Logger.get(getClass)

  /** Timer used for Leader and Reporter */
  val timer: Timer

  /** Adaptively adjusts sample rate based on storage request rate */
  val leader: ZooKeeperAdaptiveLeader

  /** Periodically reports values to ZooKeeper */
  val reporter: ZooKeeperAdaptiveReporter

  def start() {
    log.info("Start zk adaptive sampler")

    reporter.start()
    leader.start()
  }

  def shutdown() {
    log.info("Stop zk adaptive sampler")
    reporter.shutdown()
    leader.shutdown()
    timer.stop()
  }
}

object ZooKeeperAdaptiveSampler {

  /** Build a ZooKeeperAdaptiveSampler */
  def apply(
      _timer: Timer,
      _leader: ZooKeeperAdaptiveLeader,
      _reporter: ZooKeeperAdaptiveReporter): ZooKeeperAdaptiveSampler =
    new ZooKeeperAdaptiveSampler() {
      val timer    = _timer
      val leader   = _leader
      val reporter = _reporter
  }
}
