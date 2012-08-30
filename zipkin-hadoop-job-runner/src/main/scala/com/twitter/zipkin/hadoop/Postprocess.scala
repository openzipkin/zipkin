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

import config.{MailConfig, WorstRuntimesPerTraceClientConfig}
import email.{Email, EmailContent}
import com.twitter.logging.Logger

/**
 * Runs all the jobs which write to file on the input, and sends those as emails.
 * The arguments are expected to be inputdirname servicenamefile
 */
object PostprocessWriteToFile {

  val jobList = List(("WorstRuntimesPerTrace", (new WorstRuntimesPerTraceClientConfig()).apply()),
                      ("Timeouts", new TimeoutsClient()),
                      ("Retries", new RetriesClient()),
                      ("MemcacheRequest", new MemcacheRequestClient()),
                      ("ExpensiveEndpoints", new ExpensiveEndpointsClient()))


  def main(args: Array[String]) {
    val input = args(0)
    val serviceNames = args(1)
    val output = if (args.length < 3) null else args(2)

    EmailContent.populateServiceNames(serviceNames)
    for (jobTuple <- jobList) {
      val (jobName, jobClient) = jobTuple
      jobClient.start(input + "/" + jobName, output)
    }
    if (output != null) {
      EmailContent.setOutputDir(output)
      EmailContent.writeAll()
    }
    val serviceToEmail = EmailContent.writeAllAsStrings()
    for (tuple <- serviceToEmail) {
      val (service, content) = tuple
      EmailContent.getEmailAddress(service) match {
        case Some(addresses) => addresses.foreach {address => (new MailConfig()).apply().send(new Email(address, "Service Report for " + service, content))}
      }
    }
  }
}

//TODO: Replace (or supplement) this with one main method that runs all jobs

/**
 * Runs the PopularKeysClient on the input
 */
object ProcessPopularKeys {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    EmailContent.populateServiceNames(args(0))
    val c = new PopularKeyValuesClient(portNumber)
    c.start(args(0), args(1))
  }
}

/**
 * Runs the PopularAnnotationsClient on the input
 */

object ProcessPopularAnnotations {
  def main(args : Array[String]) {
    val portNumber = augmentString(args(2)).toInt
    EmailContent.populateServiceNames(args(0))
    val c = new PopularAnnotationsClient(portNumber)
    c.start(args(0), args(1))
  }
}


/**
 * Runs the MemcacheRequestClient on the input
 */

object ProcessMemcacheRequest {
  def main(args : Array[String]) {
    EmailContent.populateServiceNames(args(0))
    val c = new MemcacheRequestClient()
    c.start(args(0), args(1))
    EmailContent.writeAll()
  }
}


/**
 * Runs the TimeoutsClient on the input
 */

object ProcessTimeouts {
  def main(args : Array[String]) {
    EmailContent.populateServiceNames(args(0))
    val c = new TimeoutsClient()
    c.start(args(0), args(1))
    EmailContent.writeAll()
  }
}


/**
 * Runs the ExpensiveEndpointsClient on the input
 */

object ProcessExpensiveEndpoints {

  def main(args: Array[String]) {
    EmailContent.populateServiceNames(args(0))
    val c = new ExpensiveEndpointsClient()
    c.start(args(0), args(1))
    EmailContent.writeAll()
  }

}

object ProcessWorstRuntimesPerTrace {

  def main(args: Array[String]) {
    EmailContent.populateServiceNames(args(0))
    val c = (new WorstRuntimesPerTraceClientConfig()).apply()
    c.start(args(0), args(1))
    EmailContent.writeAll()
  }

}