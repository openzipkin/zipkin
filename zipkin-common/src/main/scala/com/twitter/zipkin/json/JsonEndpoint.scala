package com.twitter.zipkin.json

import com.google.common.net.InetAddresses.{coerceToInteger, forString}
import com.twitter.zipkin.common.Endpoint

/**
 * Container for sanitized endpoint data.
 * This differs from thrift endpoint in that port is unsigned
 * and the address is a dotted quad string.
 */
case class JsonEndpoint(serviceName: String, ipv4: String, port: Option[Int])

object JsonService extends (Endpoint => JsonEndpoint) {
  override def apply(e: Endpoint) =
    new JsonEndpoint(e.serviceName, e.getHostAddress, if (e.port == 0) None else Some(e.getUnsignedPort))

  def invert(e: JsonEndpoint) =
    new Endpoint(coerceToInteger(forString(e.ipv4)), e.port.map(_.toShort).getOrElse(0), e.serviceName)
}
