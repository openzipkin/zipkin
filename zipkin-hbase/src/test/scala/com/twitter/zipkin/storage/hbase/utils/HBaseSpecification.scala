package com.twitter.zipkin.storage.hbase.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseTestingUtility
import org.junit.runner.RunWith
import org.specs.SpecificationWithJUnit
import org.specs.runner.JUnitSuiteRunner

@RunWith(classOf[JUnitSuiteRunner])
class HBaseSpecification extends SpecificationWithJUnit {
  lazy val _util: HBaseTestingUtility = HBaseSpecification.sharedUtil
  lazy val _conf: Configuration = _util.getConfiguration

  doBeforeSpec {
    HBaseSpecification.sharedUtil.synchronized {
      _util.startMiniCluster()
    }
  }

  doAfterSpec {
    HBaseSpecification.sharedUtil.synchronized {
      _util.shutdownMiniCluster()
      Thread.sleep(10 * 1000)
    }
  }

  sequential
}

/**
 * Object to hold single util.
 */
object HBaseSpecification {
  lazy val sharedUtil = new HBaseTestingUtility()
}

