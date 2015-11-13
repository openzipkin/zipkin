/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.common

import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer

import com.google.common.collect.ComparisonChain
import com.twitter.zipkin.util.Util._

/**
 * Indicates the network context of a service involved in a span.
 *
 * @param ipv4 IPv4 host address packed into 4 bytes
 * @param port IPv4 port or 0, if unknown
 * @param serviceName classifier of a source or destination in lowercase, such as "zipkin-web".
 */
case class Endpoint(ipv4: Int, port: Short, serviceName: String) extends Ordered[Endpoint] {
  checkArgument(serviceName.toLowerCase == serviceName,
    s"serviceName must be lowercase: $serviceName")

  /**
   * Return the java.net.InetSocketAddress which contains host/port
   */
  def getInetSocketAddress: InetSocketAddress = {
    val bytes = ByteBuffer.allocate(4).putInt(this.ipv4).array()
    new InetSocketAddress(InetAddress.getByAddress(bytes), this.getUnsignedPort)
  }

  /**
   * Convenience function to get the string-based ip-address
   */
  def getHostAddress: String = {
    "%d.%d.%d.%d".format(
      (ipv4 >> 24) & 0xFF,
      (ipv4 >> 16) & 0xFF,
      (ipv4 >> 8) & 0xFF,
      ipv4 & 0xFF)
  }

  def getUnsignedPort: Int = port & 0xFFFF

  override def compare(that: Endpoint) = ComparisonChain.start()
    .compare(serviceName, that.serviceName)
    .compare(ipv4, that.ipv4)
    .compare(port, that.port)
    .result()
}

object Endpoint {
  val Unknown = Endpoint(0, 0, "")
  val UnknownServiceName = "unknown"
}
