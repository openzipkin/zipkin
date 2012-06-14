package com.twitter.zipkin.collector.processor

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

import com.twitter.finagle.TooManyWaitersException
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future

/**
 * A processor that takes an item (Span) and processes it some way.
 * Could be: storing it or aggregating the data in some way for example.
 */
trait Processor[T] {

  private val log = Logger.get

  /**
   * Process the item.
   */
  def process(item: T): Future[Unit]

  /**
   * Shut down this processor
   */
  def shutdown()

  protected def failureHandler(method: String): (Throwable) => Unit = {
    case t: TooManyWaitersException =>
    case e => {
      Stats.getCounter("exception_" + method + "_" + e.getClass).incr()
      log.error(e, method)
    }
  }
}

class NullProcessor[T] extends Processor[T] {
  def process(item: T): Future[Unit] = Future.Unit
  def shutdown() {}
}

class SequenceProcessor[T](processor: Processor[T]) extends Processor[Seq[T]] {
  def process(items: Seq[T]): Future[Unit] = {
    Future.join {
      items.map {
        processor.process(_)
      }
    }
  }

  def shutdown() { processor.shutdown() }
}
