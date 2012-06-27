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

package com.twitter.zipkin.hadoop.sources

import java.nio.ByteBuffer
import java.util.Arrays
import com.twitter.zipkin.gen.{Constants, Annotation}

/**
 * A collection of useful functions used throughout the library
 */

object Util {

  /**
   * Given a byte buffer, produces the array of bytes it represents
   * @param buf the byte buffer we need to convert to an array
   * @return the array encoding the same information as the buffer
   */
  def getArrayFromBuffer(buf: ByteBuffer): Array[Byte] = {
    val length = buf.remaining
    if (buf.hasArray()) {
      val boff = buf.arrayOffset() + buf.position()
      if (boff == 0 && length == buf.array().length) {
        buf.array()
      } else {
        Arrays.copyOfRange(buf.array(), boff, boff + length)
      }
    } else {
      val bytes = new Array[Byte](length)
      buf.duplicate.get(bytes)
      bytes
    }
  }

  /**
   * Given a list of annotations, finds the best possible service name for it. Prefers server-side names, but if
   * none exist we choose the client side name if possible. If neither exist, we return None
   * @param annotations a list of Annotations
   * @return Some(service name) if we could find a service name, None otherwise
   */
  def getServiceName(annotations : List[Annotation]) : Option[String] = {
    var service: Option[Annotation] = None
    var hasServerRecv = false
    annotations.foreach { a : Annotation =>
      if ((Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue))) {
        if (a.getHost != null) {
          if (!hasServerRecv) {
            service = Some(a)
          }
        }
      }
      if ((Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue))) {
        if (a.getHost != null) {
          service = Some(a)
          hasServerRecv = true
        }
      }
    }
    for (s <- service)
      yield s.getHost.service_name
  }

  /**
   * Given a list of annotations, finds the client's name if present and the best possible service name by the
   * same semantics as in getServiceName.
   * @param annotations a list of Annotations
   * @return Some(client's service name, service name) if a service name exists, None otherwise
   */
  def getClientAndServiceName(annotations : List[Annotation]) : Option[(String, String)] = {
    var service: Option[Annotation] = None
    var clientSend : Annotation = null
    var hasServerRecv = false
    annotations.foreach { a : Annotation =>
      if ((Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue))) {
        if (a.getHost != null) {
          if (!hasServerRecv) {
            service = Some(a)
          }
          clientSend = a
        }
      }
      if ((Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue))) {
        if (a.getHost != null) {
          service = Some(a)
          hasServerRecv = true
        }
      }
    }
    for (s <- service)
      yield if (clientSend == null) (null, s.getHost.service_name) else (clientSend.getHost.service_name, s.getHost.service_name)
  }
}
