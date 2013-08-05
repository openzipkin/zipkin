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

import java.util.concurrent.Executors
import com.twitter.util.FuturePool


object AnormThreads {

  // Cached pools automatically close threads after 60 seconds
  private val threadPool = Executors.newCachedThreadPool()

  /**
   * Execute a callback in a separate thread.
   */
  def inNewThread = FuturePool(threadPool)

}
