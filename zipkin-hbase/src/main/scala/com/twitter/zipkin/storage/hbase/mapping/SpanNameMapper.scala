package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.util.Bytes


case class SpanNameMapper(serviceMapping: ServiceMapping) extends Mapper[SpanNameMapping] {
  val mappingTable: HBaseTable = serviceMapping.mappingTable
  val typeBytes = Array[Byte](1)
  val qualBytes: Array[Byte] = Bytes.toBytes(serviceMapping.id) ++ typeBytes
  val idGen: IDGenerator = serviceMapping.idGen
  val parentId: Long = serviceMapping.id

  protected def createInternal(id: Long, value: Array[Byte]): SpanNameMapping = {
    SpanNameMapping(id, value, Some(serviceMapping))
  }
}
