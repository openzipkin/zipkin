package com.twitter.zipkin.storage.hbase.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseTestingUtility
import org.scalatest._

class HBaseSpecification extends FunSuite with Matchers with BeforeAndAfterAll {
  lazy val _util: HBaseTestingUtility = HBaseSpecification.sharedUtil
  lazy val _conf: Configuration = _util.getConfiguration

  override def beforeAll(configMap: ConfigMap) {
    HBaseSpecification.sharedUtil.synchronized {
      _util.startMiniCluster()
    }
  }

  override def afterAll(configMap: ConfigMap) {
    HBaseSpecification.sharedUtil.synchronized {
      _util.shutdownMiniCluster()
      Thread.sleep(10 * 1000)
    }
  }
}

/**
 * Object to hold single util.
 */
object HBaseSpecification {
  lazy val sharedUtil = new HBaseTestingUtility()
}

