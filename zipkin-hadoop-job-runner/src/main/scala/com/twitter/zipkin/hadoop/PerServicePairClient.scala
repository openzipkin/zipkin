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

import java.util.Scanner
import com.twitter.zipkin.gen

/**
 * Client which writes to a server, and per service pair
 * @param combineSimilarNames
 * @param portNumber
 */
abstract class PerServicePairClient(combineSimilarNames: Boolean, portNumber: Int) extends
  WriteToServerClient(combineSimilarNames, portNumber) {

  def populateServiceNameList(s: Scanner) {
    if (!combineSimilarNames) return
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      serviceNameList.add(line.next() + DELIMITER + line.next())
    }
  }

  def getKeyValue(lineScanner: Scanner) = {
    lineScanner.next() + DELIMITER + lineScanner.next()
  }
}