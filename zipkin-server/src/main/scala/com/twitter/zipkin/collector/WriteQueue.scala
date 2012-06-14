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

import com.twitter.ostrich.admin.Service
import java.util.concurrent.{Executors, ArrayBlockingQueue}
import com.twitter.ostrich.stats.Stats
import processor.Processor
import sampler.GlobalSampler
import com.twitter.zipkin.common.Span

class WriteQueue(writeQueueMaxSize: Int,
                 flusherPoolSize: Int,
                 processor: Processor[Span],
                 sampler: GlobalSampler) extends Service {

  private val queue = new ArrayBlockingQueue[List[String]](writeQueueMaxSize)
  Stats.addGauge("write_queue_qsize") { queue.size }
  private var workers: Seq[WriteQueueWorker] = Seq()

  def start() {
    workers = (0 until flusherPoolSize).toSeq map { i: Int =>
      val worker = new WriteQueueWorker(queue, processor, sampler)
      worker.start()
      worker
    }
  }

  /**
   * Will block until all entries in queue have been flushed.
   * Assumes now new entries will be added to queue.
   */
  def flushAll() {
    while(!queue.isEmpty) {
      Thread.sleep(100)
    }
  }

  def shutdown() {
    workers foreach { _.stop() }
    workers foreach { _.shutdown() }
    processor.shutdown()
  }

  def add(messages: List[String]): Boolean = {
    queue.offer(messages)
  }
}
