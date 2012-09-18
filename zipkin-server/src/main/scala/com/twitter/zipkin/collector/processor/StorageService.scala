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
package com.twitter.zipkin.collector.processor

import com.twitter.finagle.{TooManyWaitersException, Service}
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.Storage

class StorageService(storage: Storage) extends Service[Span, Unit] {

  private[this] val log = Logger.get()

  def apply(span: Span): Future[Unit] = {
    storage.storeSpan(span) onFailure {
      case t: TooManyWaitersException =>
      case e => {
        Stats.getCounter("exception_%s_%s".format("storeSpan", e.getClass)).incr()
        log.error(e, "storeSpan")
      }
    }
  }

  override def release() {
    storage.close()
  }
}
