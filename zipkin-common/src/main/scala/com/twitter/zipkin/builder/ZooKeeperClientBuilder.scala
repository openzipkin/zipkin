package com.twitter.zipkin.builder

import com.twitter.common.net.InetSocketAddressHelper
import com.twitter.common.quantity.{Time, Amount}
import com.twitter.common.zookeeper.{ZooKeeperUtils, ZooKeeperClient}
import com.twitter.ostrich.admin.{ServiceTracker, Service => OstrichService}
import scala.collection.JavaConverters._

case class ZooKeeperClientBuilder(
  hosts: Seq[String],
  port: Int = 2181,
  sessionTimeout: Amount[java.lang.Integer, Time] = ZooKeeperUtils.DEFAULT_ZK_SESSION_TIMEOUT
) extends Builder[ZooKeeperClient] {

  def sessionTimeout(s: Amount[java.lang.Integer, Time]) = copy(sessionTimeout = s)

  def apply() = {
    val hostPorts = hosts map { "%s:%d".format(_, port) }
    val socketAddress = InetSocketAddressHelper.convertToSockets(hostPorts.asJava)
    val client = new ZooKeeperClient(sessionTimeout, socketAddress)

    ServiceTracker.register(new OstrichService {
      def start() {}
      def shutdown { client.close() }
    })

    client
  }
}
