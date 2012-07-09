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

import org.apache.thrift.TException
import org.apache.thrift.protocol.TBinaryProtocol
import scala.collection.JavaConverters._
import org.apache.thrift.transport.{TFramedTransport, TSocket, TTransport, TTransportException}
import java.io.{FileNotFoundException, File}
import com.twitter.zipkin.gen
import java.net.SocketException
import java.util.{Arrays, Scanner}

/**
 * Runs the PopularKeysClient on the input
 */
object ProcessPopularKeys {
  def main(args : Array[String]) {
    val c = new PopularKeysClient()
    val portNumber = augmentString(args(2)).toInt
    val isKeyData = augmentString(args(3)).toBoolean
    c.start(args(0), args(1), portNumber, isKeyData)
  }
}

/**
 * Connects to the Zipkin Collector, then processes data from PopularKeys and sends it there. This powers the
 * typeahead functionality for annotations
 */
class PopularKeysClient {
  /**
   * Given a file name, the server name and port number, connects to the server, then writes to it
   * the top 100 key values per service name given the data in filename
   * @param filename
   * @param serverName
   * @param portNumber
   */
  def start(filename : String, serverName : String, portNumber : Int, isKeyData : Boolean) {
    var transport : TTransport = null
    try {
      // establish connection to the server
      transport = new TFramedTransport(new TSocket(serverName, portNumber))
      val protocol = new TBinaryProtocol(transport)
      val client = new gen.ZipkinCollector.Client(protocol)
      transport.open()
      // Read file
      val s = new Scanner(new File(filename))
      var line : Scanner = new Scanner(s.nextLine())
      if (!s.hasNextLine()) return
      var oldService : String = line.next()
      var keys : List[String] = List(line.next())
      while (s.hasNextLine()) {
        line = new Scanner(s.nextLine())
        val currentString = line.next()
        var value = ""
        if (line.hasNext()) value = line.next()
        while (line.hasNext()) {
          value += " " + line.next()
        }
        // Keep adding the keys to the current service's list until we are done with that service
        if (oldService != currentString) {
          // when we are, write that list to the server
          if (isKeyData)
            client.storeTopKeyValueAnnotations(oldService, keys.asJava)
          else
            client.storeTopAnnotations(oldService, keys.asJava)
          println("Writing " + keys.toString + " to service " + oldService)
          // and start processing the new one
          keys = List(value)
          oldService = currentString
        } else {
          keys = keys ::: List(value)
        }
      }
      // Write the last service in the file and its keys as well
      if (isKeyData)
        client.storeTopKeyValueAnnotations(oldService, keys.asJava)
      else
        client.storeTopAnnotations(oldService, keys.asJava)
    } catch {
      case se: SocketException => se.printStackTrace()
      case tte : TTransportException => tte.printStackTrace()
      case te : TException => te.printStackTrace()
      case e : Exception => e.printStackTrace()
    } finally {
      if (transport != null)
        transport.close()
    }
  }
}

