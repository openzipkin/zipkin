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

import com.twitter.finagle.Service
import com.twitter.ostrich.admin.BackgroundProcess
import java.util.concurrent.{TimeUnit, BlockingQueue}

class WriteQueueWorker[T](queue: BlockingQueue[T],
                       service: Service[T, _]) extends BackgroundProcess("WriteQueueWorker", false) {

  def runLoop() {
    val item = queue.poll(500, TimeUnit.MILLISECONDS)
    if (item != null) {
      process(item)
    }
  }

  private[collector] def process(t: T) {
    service(t)
  }
}
