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

import com.twitter.util.Future

/**
 * Fans out a single item to a set of `Processor`s
 * @param processors
 * @tparam T
 */
class FanoutProcessor[T](processors: Seq[Processor[T]]) extends Processor[T] {
  def process(item: T): Future[Unit] = {
    Future.join {
      processors map { _.process(item) }
    }
  }

  def shutdown() {
    processors.foreach { _.shutdown() }
  }
}
