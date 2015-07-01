/*
 * Copyright 2013 Twitter Inc.
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

import java.util.concurrent.{ThreadPoolExecutor, TimeUnit, LinkedBlockingQueue}
import com.twitter.util.FuturePool


object AnormThreads {

  // Custom pool with max of 32 threads, max of 1000 queued tasks, idle threads automatically closed after 60 seconds
  private val threadPool = new ThreadPoolExecutor(0, 32, 60, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](1000))

  // Back-pressure mechanism if pool is saturated
  threadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())

  /**
   * Execute a callback in a separate thread.
   */
  def inNewThread = FuturePool(threadPool)

}
