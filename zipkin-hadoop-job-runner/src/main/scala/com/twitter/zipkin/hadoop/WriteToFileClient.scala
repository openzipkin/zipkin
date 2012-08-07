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

import java.io._
import java.util.Scanner
import com.twitter.zipkin.gen
import collection.mutable.HashMap

/**
 * A client which writes to a file. This is intended for use mainly to format emails
 * @param combineSimilarNames
 * @param jobname
 */

abstract class WriteToFileClient(combineSimilarNames: Boolean, jobname: String) extends HadoopJobClient(combineSimilarNames) {

  protected var outputDir = ""

  def start(input: String, outputDir: String) {
    this.outputDir = outputDir
    processDir(new File(input))
  }
}

/**
 * A companion object to the WriteToFileClient which ensures that only one writer is ever open per service
 */

object WriteToFileClient {

  protected var pws : HashMap[String, PrintWriter] = new HashMap[String, PrintWriter]()

  def getWriter(s: String) = {
    if (pws.contains(s)) pws(s)
    else {
      val pw = new PrintWriter((new FileOutputStream(s, true)))
      pws += s -> pw
      pw
    }
  }

  def closeAllWriters() {
    for (s <- pws.keys) {
      pws(s).close()
    }
  }

  def writeHtmlHeader(s: String) = {
    if (!pws.contains(s)) {
      throw new IllegalArgumentException("Service " + s + " not found")
    }
    val pw = getWriter(s)

    pw.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
      "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">" +
      "\n<html>" +
      "\n<body>")
  }

  def writeHtmlClosing(s: String) = {
    if (!pws.contains(s)) {
      throw new IllegalArgumentException("Service " + s + " not found")
    }
    val pw = getWriter(s)

    pw.println("\n</body>" +
      "\n</html>")
  }
}

/**
 * A client which writes MemcacheRequest data to the file specified
 */

class MemcacheRequestClient extends WriteToFileClient(true, "MemcacheRequest") {

  def processKey(service: String, values: List[List[String]]) {
    val numberMemcacheRequests = {
      val valuesToInt = values.flatten.map({ s: String => augmentString(s).toInt })
      valuesToInt.foldLeft(0) ((left: Int, right: Int) => left + right )
    }
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    pw.println(service + " made " + numberMemcacheRequests + " redundant memcache requests")
    pw.flush()
  }

}

/**
 * A client which writes to a file, per each service pair
 */

abstract class WriteToFilePerServicePairClient(jobname: String) extends WriteToFileClient(false, jobname) {

  def writeHeader(service: String, pw: PrintWriter)

  def getTableHeader(): List[String]

  def toHtmlHeader(header: List[String]) = {
    "<tr>" + (header.map({s: String => "<th=\"col\">" + s + "</th>"}).mkString("\n")) + "</tr>"
  }

  def toHtmlListRow(sl: List[String]) = {
    "<tr>" + (sl.map({s => "<td>" + s + "</td>"}).mkString("\n")) + "</tr>"
  }

  def writeTableHeader(pw: PrintWriter) {
    pw.println(toHtmlHeader(getTableHeader()))
  }

  def writeValue(value: List[String], pw: PrintWriter) {
    pw.println(toHtmlListRow(value))
  }

  def processKey(service: String, values: List[List[String]]) {
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    writeHeader(service, pw)
    pw.println("<table>\n")
    writeTableHeader(pw)
    values.foreach {value: List[String] => writeValue(value, pw)}
    pw.println("\n</table>")
    pw.flush()
  }
}


/**
 * A client which writes Timeouts data to the file specified
 */

class TimeoutsClient extends WriteToFilePerServicePairClient("Timeouts") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("<p>" + service + " timed out in calls to the following services:</p>")
  }

  def getTableHeader() = {
    List("Service Called", "# of Timeouts")
  }
}


/**
 * A client which writes Retries data to the file specified
 */

class RetriesClient extends WriteToFilePerServicePairClient("Retries") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("<p>" + service + " retried in calls to the following services:</p>")
  }

  def getTableHeader() = {
    List("Service Called", "# of Timeouts")
  }
}


/**
 * A client which writes WorstRuntimes data to the file specified
 */

class WorstRuntimesClient extends WriteToFilePerServicePairClient("WorstRuntimes") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("<p>Service " + service + " took the longest for these spans:</p>")
  }

  def getTableHeader() = {
    List("Span ID", "Duration")
  }
}


/**
 * A client which writes WorstRuntimesPerTrace data to the file specified. Formats it as a HTML url
 */

class WorstRuntimesPerTraceClient(zipkinUrl: String) extends WriteToFilePerServicePairClient("WorstRuntimesPerTrace") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("<p>Service " + service + " took the longest for these traces:</p>")
  }

  // TODO: Use script to determine which traces are sampled, then wrap in pretty HTML

  def getTableHeader() = {
    List("Trace ID", "Duration")
  }

  override def writeValue(value: List[String], pw: PrintWriter) {
    val traceId = value(0)
    val duration = value(1)
    pw.println(toHtmlListRow(List("<a href=\"" + zipkinUrl + "/traces/" + traceId + "\">" + traceId + "</a>", duration)))
  }

}


/**
 * A client which writes ExpensiveEndpoints data to the file specified
 */

class ExpensiveEndpointsClient extends WriteToFilePerServicePairClient("ExpensiveEndpoints") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("<p>The most expensive calls for " + service + " were:</p>")
  }

  def getTableHeader() = {
    List("Service Called", "Duration")
  }
}
