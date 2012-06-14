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
 * `ProcessFilter`s are filters that can be composed on top of `Processor`s to transform
 * items between data types.
 * @tparam T input data type
 * @tparam U output data type
 */
trait ProcessorFilter[T,U] {

  def andThen[V](next: ProcessorFilter[U,V]): ProcessorFilter[T,V] =
    new ProcessorFilter[T,V] {
      def apply(item: T): V = {
        next.apply {
          ProcessorFilter.this.apply(item)
        }
      }
    }

  def andThen(processor: Processor[U]): Processor[T] = new Processor[T] {
    def process(item: T): Future[Unit] = {
      processor.process {
        ProcessorFilter.this.apply(item)
      }
    }

    def shutdown() { processor.shutdown() }
  }

  def apply(item: T): U
}
