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
package com.twitter.zipkin.storm

import backtype.storm.spout.Scheme
import backtype.storm.tuple.Fields
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Return, Throw, Try}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import java.util.Arrays
import scala.collection.JavaConverters._

/**
 * Spout scheme to turn incoming data into Span fields
 */
class SpanScheme extends Scheme {
  @transient private val log = Logger.get
  lazy val deserializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  override def deserialize(bytes: Array[Byte]) = {
    try {
      val s = deserializer.fromBytes(bytes).toSpan
      Arrays.asList(
        s.traceId.asInstanceOf[java.lang.Long],
        s.id.asInstanceOf[java.lang.Long],
        s.name,
        s.serviceName.getOrElse(""),
        s.isClientSide.asInstanceOf[java.lang.Boolean])
    } catch {
      case e: Exception => {
        log.warning(e, "Invalid bytes for deserializer")
        Stats.incr("spout.invalid_bytes")
        Arrays.asList("")
      }
    }
  }

  override def getOutputFields =
    new Fields("traceId", "spanId", "name", "serviceName", "isClientSide")
}