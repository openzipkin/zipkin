package com.twitter.zipkin.hbase


import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.Index
import com.twitter.zipkin.storage.hbase.HBaseIndex
import com.twitter.zipkin.storage.hbase.utils.{ThreadProvider, ConfBuilder, HBaseTable}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration

case class IndexBuilder(
  confOption: Option[Configuration] = Some(HBaseConfiguration.create()),
  zkServers: Option[String] = None,
  zkPort: Option[Int] = None,
  durationTableName:String = TableLayouts.durationTableName,
  idxServiceTableName:String = TableLayouts.idxServiceTableName,
  idxServiceSpanNameTableName:String = TableLayouts.idxServiceSpanNameTableName,
  idxServiceAnnotationTableName:String = TableLayouts.idxServiceAnnotationTableName,
  mappingTableName:String = TableLayouts.mappingTableName,
  idGenTableName:String = TableLayouts.idGenTableName
) extends Builder[Index] with ConfBuilder {
  self =>

  def conf(conf: Configuration):IndexBuilder = copy(confOption = Some(conf))

  def zkServers(zks: String):IndexBuilder = copy(zkServers = Some(zks))

  def zkPort(zkp: Int):IndexBuilder = copy(zkPort = Some(zkp))

  def durationTableName(tableName:String):IndexBuilder = copy(durationTableName = tableName)

  def indexServiceTableName(tableName:String):IndexBuilder = copy(idxServiceTableName = tableName)

  def indexServiceSpanNameTable(tableName:String):IndexBuilder = copy(idxServiceSpanNameTableName = tableName)

  def indexServiceAnnotationTableName(tableName:String):IndexBuilder = copy(idxServiceAnnotationTableName = tableName)

  def mappingTableName(tableName:String):IndexBuilder = copy(mappingTableName = tableName)

  def idGenerationTableName(tableName:String):IndexBuilder = copy(idGenTableName = tableName)

  def apply() = {

    // Create the HBaseIndex supplying all of the tables.
    new HBaseIndex {
      val durationTable = new HBaseTable(conf, durationTableName)
      val idxServiceTable = new HBaseTable(conf, idxServiceTableName, mainExecutor = ThreadProvider.indexServiceExecutor)
      val idxServiceSpanNameTable = new HBaseTable(conf, idxServiceSpanNameTableName, mainExecutor = ThreadProvider.indexServiceSpanExecutor)
      val idxServiceAnnotationTable = new HBaseTable(conf, idxServiceAnnotationTableName, mainExecutor = ThreadProvider.indexAnnotationExecutor)

      val mappingTable = new HBaseTable(conf, mappingTableName, mainExecutor = ThreadProvider.mappingTableExecutor)
      val idGenTable = new HBaseTable(conf, idGenTableName, mainExecutor = ThreadProvider.idGenTableExecutor)
    }
  }
}

