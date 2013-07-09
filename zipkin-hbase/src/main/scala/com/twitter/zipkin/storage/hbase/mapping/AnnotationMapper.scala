package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.util.Bytes

case class AnnotationMapper(serviceMapping: ServiceMapping) extends Mapper[AnnotationMapping] {
  val mappingTable: HBaseTable = serviceMapping.mappingTable
  val typeBytes = Array[Byte](2)
  val qualBytes: Array[Byte] = Bytes.toBytes(serviceMapping.id) ++ typeBytes
  val idGen: IDGenerator = serviceMapping.idGen
  val parentId: Long = serviceMapping.id

  protected def createInternal(id: Long, value: Array[Byte]): AnnotationMapping = {
    AnnotationMapping(id, value, Some(serviceMapping))
  }
}
