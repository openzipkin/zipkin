package com.twitter.zipkin.hadoop

import java.io._
import java.util.Scanner
import com.twitter.zipkin.gen
import collection.mutable.HashMap

abstract class WriteToFileClient(combineSimilarNames: Boolean, jobname: String) extends HadoopJobClient(combineSimilarNames) {

  protected var outputDir = ""

  def populateSsnm(s: Scanner) {
    if (!combineSimilarNames) return
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      ssnm.add(getKeyValue(line))
    }
  }

  def getKeyValue(lineScanner: Scanner) = {
    lineScanner.next().replace('/', '.')
  }

  def start(input: String, outputDir: String) {
    this.outputDir = outputDir
    populateSsnm(new Scanner(new File(input)))
    processFile(new Scanner(new File(input)))
  }
}

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

}

class MemcacheRequestClient extends WriteToFileClient(true, "MemcacheRequest") {

  def processKey(service: String, values: List[String]) {
    val numberMemcacheRequests = {
      val valuesToInt = values map ({ s: String => augmentString(s).toInt })
      valuesToInt.foldLeft(0) ((left: Int, right: Int) => left + right )
    }
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    pw.println(service + " made " + numberMemcacheRequests + " redundant memcache requests")
    pw.flush()
  }

}

abstract class WriteToFilePerServicePairClient(jobname: String) extends WriteToFileClient(false, jobname) {

  def writeHeader(service: String, pw: PrintWriter)

  def writeValue(value: String, pw: PrintWriter)

  def processKey(service: String, values: List[String]) {
    val pw = WriteToFileClient.getWriter(outputDir + "/" + service)
    writeHeader(service, pw)
    values.foreach {value: String => writeValue(value, pw)}
    pw.flush()
  }
}

class TimeoutsClient extends WriteToFilePerServicePairClient("Timeouts") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println(service + " timed out in calls to the following services:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}

class RetriesClient extends WriteToFilePerServicePairClient("Retries") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println(service + " retried in calls to the following services:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}

class WorstRuntimesClient extends WriteToFilePerServicePairClient("WorstRuntimes") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("Service " + service + " took the longest for these spans:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}

class WorstRuntimesPerTraceClient extends WriteToFilePerServicePairClient("WorstRuntimesPerTrace") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("Service " + service + " took the longest for these traces:")
  }

  // TODO: Use script to determine which traces are sampled, then wrap in pretty HTML
  def writeValue(value: String, pw: PrintWriter) {
    pw.println("<a href=\"https://zipkin.smf1.twitter.com/traces/" + value + "\">" + value + "</a>")
  }

}

class ExpensiveEndpointsClient extends WriteToFilePerServicePairClient("ExpensiveEndpoints") {

  def writeHeader(service: String, pw: PrintWriter) {
    pw.println("The most expensive calls for " + service + " were:")
  }

  def writeValue(value: String, pw: PrintWriter) {
    pw.println(value)
  }

}
