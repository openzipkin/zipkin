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

import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.zipkin.gen
import java.util.Scanner
import java.io.File
import java.net.SocketException
import org.apache.thrift.transport.{TTransportException, TSocket, TFramedTransport, TTransport}
import org.apache.thrift.TException

abstract class WriteToServerClient(combineSimilarNames: Boolean, portNumber: Int) extends HadoopJobClient(combineSimilarNames) {

  protected var client : gen.ZipkinCollector.Client = null

  def start(filename: String, serverName: String) {
    var transport : TTransport = null
    try {
      // establish connection to the server
      transport = new TFramedTransport(new TSocket(serverName, portNumber))
      val protocol = new TBinaryProtocol(transport)
      client = new gen.ZipkinCollector.Client(protocol)
      transport.open()
      // Read file
      println("Connected to server...populating ssnm")
      populateSsnm(new Scanner(new File(filename)))
      println("Finished populating ssnm...beginning processFile")
      processFile(new Scanner(new File(filename)))
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
