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

import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.adapter.ThriftAdapter
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.gen

class ScribeProcessorFilter extends ProcessorFilter[Seq[gen.LogEntry], Seq[Span]] {

  private val log = Logger.get

  val deserializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  def apply(logEntries: Seq[gen.LogEntry]): Seq[Span] = {
    logEntries.map {
      _.`message`
    }.flatMap {
      msg =>
        try {
          val span = Stats.time("deserializeSpan") {
            deserializer.fromString(msg)
          }
          log.ifDebug("Processing span: " + span + " from " + msg)
          Some(ThriftAdapter(span))
        } catch {
          case e: Exception => {
            // scribe doesn't have any ResultCode.ERROR or similar
            // let's just swallow this invalid msg
            log.warning(e, "Invalid msg: %s", msg)
            Stats.incr("collector.invalid_msg")
            None
          }
        }
    }
  }
}
