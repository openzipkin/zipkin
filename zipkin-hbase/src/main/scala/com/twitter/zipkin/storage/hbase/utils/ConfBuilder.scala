package com.twitter.zipkin.storage.hbase.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{HConstants, HBaseConfiguration}

/**
 * Trait used to create Hadoop conf from different passed in params.
 */
trait ConfBuilder {

  val confOption : Option[Configuration]
  val zkServers: Option[String]
  val zkPort: Option[Int]

  /**
   * If the user didn't pass a conf, instead the passed zk hostname and port
   * then we need to create a hadoop conf for containing that info
   * @return
   */
  private def createConf() = {
    val conf = HBaseConfiguration.create()
    zkServers.foreach { q => conf.set(HConstants.ZOOKEEPER_QUORUM, q ) }
    zkPort.foreach { p => conf.set(HConstants.ZOOKEEPER_CLIENT_PORT, p.toString) }
    conf
  }

  lazy val conf = confOption.getOrElse { createConf() }
}
