/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.collector

import com.twitter.ostrich.admin.{Service => OService}
import com.twitter.ostrich.stats.Stats
import java.util.concurrent.ArrayBlockingQueue
import com.twitter.finagle.Service

class WriteQueue[T](writeQueueMaxSize: Int,
                 flusherPoolSize: Int,
                 service: Service[T, _]) extends OService {

  private val queue = new ArrayBlockingQueue[T](writeQueueMaxSize)
  Stats.addGauge("write_queue_qsize") { queue.size }
  private var workers: Seq[WriteQueueWorker[T]] = Seq()
  @volatile var running: Boolean = false

  def start() {
    workers = (0 until flusherPoolSize).toSeq map { i: Int =>
      val worker = new WriteQueueWorker[T](queue, service)
      worker.start()
      worker
    }
    running = true
  }

  /**
   * Will block until all entries in queue have been flushed.
   * Assumes now new entries will be added to queue.
   */
  private def flushAll() {
    while(!queue.isEmpty) {
      Thread.sleep(100)
    }
  }

  def shutdown() {
    running = false
    flushAll()
    workers foreach { _.stop() }
    workers foreach { _.shutdown() }
    service.release()
  }

  def add(messages: T): Boolean = {
    if (running) {
      queue.offer(messages)
    } else {
      false
    }
  }
}
