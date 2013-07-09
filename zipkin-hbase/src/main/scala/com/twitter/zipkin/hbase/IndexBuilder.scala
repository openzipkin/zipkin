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
  zkPort: Option[Int] = None
) extends Builder[Index] with ConfBuilder {
  self =>

  def apply() = {

    // Create the HBaseIndex supplying all of the tables.
    new HBaseIndex {
      val durationTable = new HBaseTable(conf, TableLayouts.durationTableName)
      val idxServiceTable = new HBaseTable(conf, TableLayouts.idxServiceTableName, mainExecutor = ThreadProvider.indexServiceExecutor)
      val idxServiceSpanNameTable = new HBaseTable(conf, TableLayouts.idxServiceSpanNameTableName, mainExecutor = ThreadProvider.indexServiceSpanExecutor)
      val idxServiceAnnotationTable = new HBaseTable(conf, TableLayouts.idxServiceAnnotationTableName, mainExecutor = ThreadProvider.indexAnnotationExecutor)

      val mappingTable = new HBaseTable(conf, TableLayouts.mappingTableName, mainExecutor = ThreadProvider.mappingTableExecutor)
      val idGenTable = new HBaseTable(conf, TableLayouts.idGenTableName, mainExecutor = ThreadProvider.idGenTableExecutor)
    }
  }
}

