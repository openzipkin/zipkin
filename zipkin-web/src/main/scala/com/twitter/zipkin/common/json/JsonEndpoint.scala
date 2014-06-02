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
package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Endpoint

/**
 * Container for sanitized endpoint data.
 * This differs from thrift endpoint in that port is unsigned
 * and the address is a dotted quad string.
 */
case class JsonEndpoint(ipv4: String, port: Int, serviceName: String)  extends WrappedJson

object JsonEndpoint {
  def wrap(host: Endpoint) = {
    new JsonEndpoint(host.getHostAddress, host.getUnsignedPort, host.serviceName)
  }
}

