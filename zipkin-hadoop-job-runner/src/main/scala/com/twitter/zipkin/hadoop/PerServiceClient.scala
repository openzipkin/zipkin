package com.twitter.zipkin.hadoop

import java.util.Scanner
import com.twitter.zipkin.gen
import scala.collection.JavaConverters._

abstract class PerServiceClient(combineSimilarNames: Boolean, portNumber: Int) extends
  WriteToServerClient(combineSimilarNames, portNumber) {

  def populateSsnm(s: Scanner) {
    if (!combineSimilarNames) return
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      ssnm.add(line.next())
    }
  }

  def getKeyValue(lineScanner: Scanner) = {
    lineScanner.next()
  }
}

/**
 * Connects to the Zipkin Collector, then processes data from PopularKeys and sends it there. This powers the
 * typeahead functionality for annotations
 */

class PopularAnnotationsClient(portNumber: Int) extends PerServiceClient(false, portNumber) {

  def processKey(service: String, values: List[String]) {
    println("Writing " + values.mkString(", ") + " to " + service)
    client.storeTopAnnotations(service, values.asJava)
  }

}

class PopularKeyValuesClient(portNumber: Int) extends PerServiceClient(false, portNumber) {

  def processKey(service: String, values: List[String]) {
    client.storeTopKeyValueAnnotations(service, values.asJava)
  }

}