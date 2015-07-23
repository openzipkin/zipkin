package com.twitter.zipkin.storage.hbase

import com.twitter.zipkin.hbase.TableLayouts
import org.apache.hadoop.hbase.util.Bytes
import com.twitter.zipkin.storage.hbase.utils.HBaseSpecification
import org.scalatest.{BeforeAndAfter, ConfigMap}

trait ZipkinHBaseSpecification extends HBaseSpecification with BeforeAndAfter {
  /**
   * The list of tables that will be avaliable for tests.
   */
  val tablesNeeded:Seq[String]

  override def beforeAll(configMap: ConfigMap) {
    super.beforeAll(configMap)
    // Grab a lock on the util to make sure we're the only one making changes
    HBaseSpecification.sharedUtil.synchronized {
      TableLayouts.createTables(_util.getHBaseAdmin, tablesNeeded, None)
    }
  }

  def before {
    HBaseSpecification.sharedUtil.synchronized {
      tablesNeeded.foreach { tableName =>
        _util.truncateTable(Bytes.toBytes(tableName))
      }
    }
  }
}

