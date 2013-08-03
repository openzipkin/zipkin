package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.util.Bytes

case class ServiceMapping(id: Long, value: Array[Byte], mappingTable: HBaseTable, idGen: IDGenerator) extends Mapping {
  val parent: Option[Mapping] = None
  val annotationMapper = new AnnotationMapper(this)
  val spanNameMapper = new SpanNameMapper(this)
  lazy val name = Bytes.toString(value)
}

