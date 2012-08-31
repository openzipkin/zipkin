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

import collection.mutable
import collection.immutable.HashMap
import email.EmailContent
import java.util.Scanner
import java.io.File
import com.twitter.zipkin.hadoop.sources._

/**
 * Basic client for postprocessing hadoop jobs
 * @param combineSimilarNames whether or not we should differentiate between similar names
 */

abstract class HadoopJobClient(val combineSimilarNames: Boolean) {

  /**
   * Process a key and its value
   * @param s the key passed
   * @param lines values associated with the key
   */
  def processKey(s: String, lines: List[LineResult])

  /**
   * Starts the postprocessing for the client
   * @param filename the input filename
   */
  def start(filename : String, output : String)

  def getServiceName(service: String) = {
    service
  }

  def getLineResult(line: List[String]): LineResult = {
    new PerServiceLineResult(line)
  }

  /**
   * Processes a single directory, with data files expected in TSV format, with key being the first value on each row
   * @param file a file representing a directory where the data is stored
   */
  def processDir(file: File) {
    var serviceToValues = new mutable.HashMap[String, List[LineResult]]()
    Util.traverseFileTree(file)({ f: File =>
      val s = new Scanner(f)
      while (s.hasNextLine()) {
        val line = getLineResult(s.nextLine.split("\t").toList.map({_.trim()}))
        val serviceName = line.getKey()
        if (serviceToValues.contains(serviceName)) {
          serviceToValues(serviceName) ++= List(line)
        } else {
          serviceToValues += serviceName -> List(line)
        }
      }
    })
    for (t <- serviceToValues) {
      val (service, values) = t
      processKey(service, values)
    }
  }
}

object HadoopJobClient {

  val DELIMITER = ":"

}