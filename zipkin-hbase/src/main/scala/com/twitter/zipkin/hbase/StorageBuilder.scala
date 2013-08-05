package com.twitter.zipkin.hbase

import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.Storage
import com.twitter.zipkin.storage.hbase.HBaseStorage
import com.twitter.zipkin.storage.hbase.utils.{ThreadProvider, ConfBuilder, HBaseTable}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration

case class StorageBuilder(
  confOption: Option[Configuration] = Some(HBaseConfiguration.create()),
  zkServers: Option[String] = None,
  zkPort: Option[Int] = None
) extends Builder[Storage] with ConfBuilder {
  self =>

  def apply() = {
    new HBaseStorage {
      val hbaseTable = new HBaseTable(conf, TableLayouts.storageTableName, mainExecutor = ThreadProvider.storageExecutor)
    }
  }
}