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

package com.twitter.zipkin.hadoop

object PostprocessWriteToFile {

  val jobList = List(("WorstRuntimesPerTrace", new WorstRuntimesPerTraceClient("https://zipkin.smf1.twitter.com")),
                      ("Timeouts", new TimeoutsClient()),
                      ("Retries", new RetriesClient()),
                      ("ProcessMemcacheRequest", new MemcacheRequestClient()),
                      ("ProcessExpensiveEndpoints", new ExpensiveEndpointsClient()))

  def main(args: Array[String]) {
    val input = args(0)
    val output = args(1)


  }
}

//TODO: Replace (or supplement) this with one main method that runs all jobs

/**
 * Runs the PopularKeysClient on the input
 */
object ProcessPopularKeys {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    val c = new PopularKeyValuesClient(portNumber)
    c.populateAndStart(args(0), args(1))
  }
}

/**
 * Runs the PopularAnnotationsClient on the input
 */

object ProcessPopularAnnotations {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    println("Arguments: " + args.mkString(", "))
    val c = new PopularAnnotationsClient(portNumber)
    c.populateAndStart(args(0), args(1))
  }
}


/**
 * Runs the MemcacheRequestClient on the input
 */

object ProcessMemcacheRequest {
  def main(args : Array[String]) {
    val c = new MemcacheRequestClient()
    c.populateAndStart(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }
}


/**
 * Runs the TimeoutsClient on the input
 */

object ProcessTimeouts {
  def main(args : Array[String]) {
    val c = new TimeoutsClient()
    c.populateAndStart(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }
}


/**
 * Runs the ExpensiveEndpointsClient on the input
 */

object ProcessExpensiveEndpoints {

  def main(args: Array[String]) {
    val c = new ExpensiveEndpointsClient()
    c.populateAndStart(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }

}

object ProcessWorstRuntimesPerTrace {

  def main(args: Array[String]) {
    val c = new WorstRuntimesPerTraceClient("https://zipkin.smf1.twitter.com")
    c.populateAndStart(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }

}