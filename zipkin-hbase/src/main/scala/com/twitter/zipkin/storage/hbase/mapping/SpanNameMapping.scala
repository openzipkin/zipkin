package com.twitter.zipkin.storage.hbase.mapping

import org.apache.hadoop.hbase.util.Bytes

case class SpanNameMapping(id: Long, value: Array[Byte], parent: Option[ServiceMapping]) extends Mapping {
  val mappingType: Byte = 1
  lazy val name = Bytes.toString(value)
}
