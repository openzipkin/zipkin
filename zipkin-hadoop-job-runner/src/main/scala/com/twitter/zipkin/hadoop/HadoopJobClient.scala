package com.twitter.zipkin.hadoop

import collection.mutable.HashMap
import com.twitter.zipkin.gen
import java.util.Scanner

abstract class HadoopJobClient(val combineSimilarNames: Boolean) {

  protected val DELIMITER = ":"
  val ssnm = if (combineSimilarNames) new StandardizedServiceNameList else new ServiceNameList

  def populateSsnm(s: Scanner)

  def getKeyValue(lineScanner: Scanner): String

  def processKey(s: String, value: List[String])

  def start(filename : String, output : String)

  def processFile(s: Scanner) {
    println("Started processFile")
    var serviceToValues = new HashMap[String, List[String]]()
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      val currentString = getKeyValue(line).trim()
      var value = ""
      if (line.hasNext()) value = line.next()
      while (line.hasNext()) {
        value += " " + line.next()
      }
      val serviceName = ssnm.getName(currentString)
      if (serviceToValues.contains(serviceName)) {
        serviceToValues(serviceName) ::= value
      } else {
        serviceToValues += serviceName -> List(value)
      }
    }
    println(serviceToValues)
    for (t <- serviceToValues) {
      val (service, values) = t
      println(service + ", " + values)
      processKey(service, values)
    }
  }
}