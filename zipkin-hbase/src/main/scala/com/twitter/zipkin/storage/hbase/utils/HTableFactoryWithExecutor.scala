package com.twitter.zipkin.storage.hbase.utils

import java.util.concurrent.ExecutorService
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{HTable, HTableInterface, HTableFactory}

case class HTableFactoryWithExecutor(executor: ExecutorService) extends HTableFactory {
  override def createHTableInterface(config: Configuration, tableName: Array[Byte]): HTableInterface = {
    new HTable(config, tableName, executor)
  }

}
