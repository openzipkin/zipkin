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

/**
 * Represents the client or server machine we traced.
 */
object Endpoint {
  val Unknown = Endpoint(0, 0, "")
  val UnknownServiceName = "Unknown service name"
}

/**
 * @param ipv4 ipv4 ip address.
 * @param port note that due to lack of unsigned integers this will wrap.
 * @param serviceName the service this operation happened on
 */
case class Endpoint(ipv4: Int, port: Short, serviceName: String)
  extends Ordered[Endpoint] {

  override def compare(that: Endpoint) = {
    if (serviceName != that.serviceName)
      serviceName compare that.serviceName
    else if (ipv4 != that.ipv4)
      ipv4 compare that.ipv4
    else if (port != that.port)
      port compare that.port
    else
      0
  }
}
