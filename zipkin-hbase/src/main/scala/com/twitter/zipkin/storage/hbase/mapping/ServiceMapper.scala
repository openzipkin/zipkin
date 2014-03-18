package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}

case class ServiceMapper(mappingTable: HBaseTable, idGen: IDGenerator) extends Mapper[ServiceMapping] {
  val qualBytes: Array[Byte] = Array[Byte](0, 0)
  val typeBytes = Array[Byte](0)
  val parentId: Long = 0

  protected def createInternal(id: Long, value: Array[Byte]): ServiceMapping = {
    ServiceMapping(id, value, mappingTable, idGen)
  }
}
