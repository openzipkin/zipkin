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

import collection.mutable.HashMap
import com.twitter.zipkin.gen
import java.util.Scanner

/**
 * Basic client for postprocessing hadoop jobs
 * @param combineSimilarNames whether or not we should differentiate between similar names
 */

abstract class HadoopJobClient(val combineSimilarNames: Boolean) {

  protected val DELIMITER = ":"
  val serviceNameList = if (combineSimilarNames) new StandardizedServiceNameList else new ServiceNameList

  /**
   * Populate the name list
   * @param s Scanner representing the name list
   */

  def populateServiceNameList(s: Scanner)

  /**
   * Returns the key value of a line
   * @param lineScanner Scanner for a single line
   * @return the key value returned
   */
  def getKeyValue(lineScanner: Scanner): String

  /**
   * Process a key and its value
   * @param s the key passed
   * @param value values associated with the key
   */
  def processKey(s: String, value: List[String])

  /**
   * Starts the postprocessing for the client
   * @param filename the input filename
   * @param output the output filename
   */
  def start(filename : String, output : String)

  /**
   * Processes a single file, expected in TSV format, with key being the first value on each row
   * @param s a Scanner representing a file
   */
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
      val serviceName = serviceNameList.getName(currentString)
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