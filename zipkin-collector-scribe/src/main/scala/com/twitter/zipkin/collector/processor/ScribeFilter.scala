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

import com.twitter.finagle.{Service, Filter}
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.Future
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.gen

/**
 * Transforms a `Seq[gen.LogEntry]` to `Seq[Span]` for a collector service to consume.
 * Assumes:
 *   - the Scribe struct contains a `message` that is the Base64 encoded Thrift Span struct.
 *   - the sequence of `LogEntry`s only contains messages we want to pass on (already filtered
 *     by category)
 */
class ScribeFilter extends Filter[Seq[String], Unit, Span, Unit] {
  private val log = Logger.get

  val deserializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  def apply(logEntries: Seq[String], service: Service[Span, Unit]): Future[Unit] = {
    Future.join {
      logEntries.map { msg =>
        try {
          val span = Stats.time("deserializeSpan") {
            deserializer.fromString(msg)
          }
          log.ifDebug("Processing span: " + span + " from " + msg)
          service(span.toSpan)
        } catch {
          case e: Exception => {
            // scribe doesn't have any ResultCode.ERROR or similar
            // let's just swallow this invalid msg
            log.warning(e, "Invalid msg: %s", msg)
            Stats.incr("collector.invalid_msg")
            Future.Unit
          }
        }
      }
    }
  }
}
