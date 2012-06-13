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

import com.twitter.zipkin.gen
import com.twitter.zipkin.common.Span
import com.twitter.logging.Logger
import com.twitter.util.Future
import processor.Processor
import sampler.GlobalSampler
import com.twitter.ostrich.stats.Stats
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.ostrich.admin.BackgroundProcess
import java.util.concurrent.{TimeUnit, BlockingQueue}

class WriteQueueWorker(queue: BlockingQueue[List[String]],
                       processors: Seq[Processor],
                       sample: GlobalSampler) extends BackgroundProcess("WriteQueueWorker", false) {

  private val log = Logger.get

  val deserializer = new BinaryThriftStructSerializer[gen.Span] { def codec = gen.Span }

  def runLoop() = {
    val item = queue.poll(500, TimeUnit.MILLISECONDS)
    if (item ne null) {
      item foreach (processScribeMessage(_))
    }
  }

  def processScribeMessage(msg: String) {
    try {
      val span = Stats.time("deserializeSpan") { deserializer.fromString(msg) }
      log.ifDebug("Processing span: " + span + " from " + msg)
      processSpan(Span.fromThrift(span))
    } catch {
      case e: Exception => {
        // scribe doesn't have any ResultCode.ERROR or similar
        // let's just swallow this invalid msg
        log.warning(e, "Invalid msg: %s", msg)
        Stats.incr("collector.invalid_msg")
      }
    }
  }

  def processSpan(span: Span) {
    try {
      span.serviceNames.foreach { name => Stats.incr("received_" + name) }

      // check if we want to store this particular trace or not
      if (sample(span.traceId)) {
        Stats.time("processSpan") {
          span.serviceNames.foreach { name => Stats.incr("process_" + name) }
          Future.join {
            processors map { _.processSpan(span) }
          } onSuccess { e =>
            Stats.incr("collector.processSpan_success")
          } onFailure { e =>
            Stats.incr("collector.processSpan_failed")
          }
        }
      }
    } catch {
      case e: Exception =>
        log.error(e, "Processing of " + span + " failed %s", e)
        Stats.incr("collector.invalid_msg")
    }
  }
}
