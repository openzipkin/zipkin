package com.twitter.zipkin.hadoop.sources

import java.nio.ByteBuffer
import java.util.Arrays
import com.twitter.zipkin.gen.{Constants, Annotation}

object Util {

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

  def getServiceName(annotations : List[Annotation]) = {
    var service: Option[Annotation] = None
    var hasServRecv = false
    annotations.foreach { a : Annotation =>
      if (Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue)) {
        if (!hasServRecv) {
          service = Some(a)
        }
      } else if (Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue)) {
        service = Some(a)
        hasServRecv = true
      }
    }
    for (sr <- service)
      yield sr.getHost.service_name
  }

  def getClientAndServiceName(annotations : List[Annotation]) = {
    var service: Option[Annotation] = None
    var clientSend : Annotation = null
    var hasServerRecv = false
    annotations.foreach { a : Annotation =>
      if ((Constants.CLIENT_SEND.equals(a.getValue) || Constants.CLIENT_RECV.equals(a.getValue))) {
        if (!hasServerRecv) {
          service = Some(a)
        }
        clientSend = a
      }
      if ((Constants.SERVER_RECV.equals(a.getValue) || Constants.SERVER_SEND.equals(a.getValue))) {
        service = Some(a)
        hasServerRecv = true
      }
    }
    for (s <- service)
    yield {
      val name = if (s.getHost == null) "Unknown Service Name" else s.getHost.service_name
      if (clientSend == null) {
        (null, name)
      } else {
        val cName = if (clientSend.getHost == null) "Unknown Service Name" else clientSend.getHost.service_name
        (cName, name)
      }
    }
  }
}
