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

/**
 * Runs the PopularKeysClient on the input
 */
object ProcessPopularKeys {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    val c = new PopularKeyValuesClient(portNumber)
    c.start(args(0), args(1))
  }
}

object ProcessPopularAnnotations {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    println("Arguments: " + args.mkString(", "))
    val c = new PopularAnnotationsClient(portNumber)
    c.start(args(0), args(1))
  }
}

object ProcessMemcacheRequest {
  def main(args : Array[String]) {
    val c = new MemcacheRequestClient()
    c.start(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }
}

object ProcessTimeouts {
  def main(args : Array[String]) {
    val c = new TimeoutsClient()
    c.start(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }
}

object ProcessExpensiveEndpoints {

  def main(args: Array[String]) {
    val c = new ExpensiveEndpointsClient()
    c.start(args(0), args(1))
    WriteToFileClient.closeAllWriters()
  }

}

