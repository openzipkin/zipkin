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
import sources.Util

/**
 * Client which writes to a server, and per service pair
 * @param combineSimilarNames
 * @param portNumber
 */
abstract class PerServicePairClient(combineSimilarNames: Boolean, portNumber: Int) extends
  WriteToServerClient(combineSimilarNames, portNumber) {

  override def getKeyValue(line: List[String]) = {
    getServiceName(Util.toServiceName(line.head)) + HadoopJobClient.DELIMITER + getServiceName(Util.toServiceName(line.tail.head))
  }

//  override def addKey(key: String) = {
//    val pairAsList = key.split(HadoopJobClient.DELIMITER)
//    HadoopJobClient.serviceNameSet.addServiceNamePair(pairAsList(0), pairAsList(1))
//  }

  override def getValue(line: List[String]) = {
    line.tail.tail
  }
}