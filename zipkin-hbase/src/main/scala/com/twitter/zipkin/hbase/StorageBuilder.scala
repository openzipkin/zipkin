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
  zkPort: Option[Int] = None,
  storageTableName:String = TableLayouts.storageTableName
) extends Builder[Storage] with ConfBuilder {
  self =>

  def conf(conf: Configuration):StorageBuilder = copy(confOption = Some(conf))

  def zkServers(zks: String):StorageBuilder = copy(zkServers = Some(zks))

  def zkPort(zkp: Int):StorageBuilder = copy(zkPort = Some(zkp))

  def storageTableName(tableName:String):StorageBuilder = copy(storageTableName = tableName)

  def apply() = {
    new HBaseStorage {
      val hbaseTable = new HBaseTable(conf, storageTableName, mainExecutor = ThreadProvider.storageExecutor)
    }
  }
}