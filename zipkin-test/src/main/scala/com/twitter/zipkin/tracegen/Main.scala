package com.twitter.zipkin.tracegen

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
import com.twitter.logging.Logger
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Generates a couple of semi-realistic traces and
 * sends to the collector. It's all pretty hacky.
 */
object Main {
  val log = Logger.get(getClass.getName)

  def main(args: Array[String]) {
    val (collectorHost, collectorPort, queryHost, queryPort) =
      if (args.length < 4) {
        // Default to localhost:9410, localhost:9411
        ("localhost", 9410, "localhost", 9411)
      } else {
        (args(0), args(1).toInt, args(2), args(3).toInt)
      }
    println("Collector: %s:%d; Query: %s:%d".format(collectorHost, collectorPort, queryHost, queryPort))

    val traceGen = new TraceGen
    val traces = traceGen.generate(1, 7)
    val requests = new Requests(collectorHost, collectorPort, queryHost, queryPort)

    requests.logTraces(traces)
    requests.querySpan("servicenameexample_0", "methodcallfairlylongname_0", "some custom annotation",
      ("key", ByteBuffer.wrap("value".getBytes)), 10)
  }
}
