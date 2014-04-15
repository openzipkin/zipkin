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
package com.twitter.zipkin.collector

import com.twitter.util.{Await, Closable, CloseAwaitably, Future, Time}

/**
 * SpanReceivers are nothing special. They need only allow us to Await on them
 * and close them.
 *
 * At creation a SpanReceiver will be provided a function of Seq[Span] => Future[Unit]
 * that it should use to process incoming spans.
 */
trait SpanReceiver extends Closable with CloseAwaitably

object SpanReceiver {
  def multiReceiver(receivers: SpanReceiver*): SpanReceiver = new SpanReceiver {
    def close(deadline: Time): Future[Unit] = closeAwaitably {
      Closable.all(receivers: _*).close(deadline)
    }
  }
}
