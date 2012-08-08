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

import scala.collection.JavaConverters._

/**
 * Client which writes to the server, and which writes per service
 * @param portNumber the port number to write to
 */

abstract class PerServiceClient(combineSimilarNames: Boolean, portNumber: Int) extends
  WriteToServerClient(combineSimilarNames, portNumber) {
}

/**
 * Connects to the Zipkin Collector, then processes data from PopularAnnotations and sends it there. This powers the
 * typeahead functionality for annotations
 */

class PopularAnnotationsClient(portNumber: Int) extends PerServiceClient(false, portNumber) {

  def processKey(service: String, values: List[List[String]]) {
    println("Writing " + values.flatten.mkString(", ") + " to " + service)
    client.storeTopAnnotations(service, values.flatten.asJava)
  }

}

/**
 * Connects to the Zipkin Collector, then processes data from PopularKeys and sends it there. This powers the
 * typeahead functionality for annotations
 */

class PopularKeyValuesClient(portNumber: Int) extends PerServiceClient(false, portNumber) {

  def processKey(service: String, values: List[List[String]]) {
    client.storeTopKeyValueAnnotations(service, values.flatten.asJava)
  }

}