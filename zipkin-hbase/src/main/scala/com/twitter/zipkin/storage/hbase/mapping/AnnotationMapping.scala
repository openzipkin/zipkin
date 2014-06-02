package com.twitter.zipkin.storage.hbase.mapping

case class AnnotationMapping(id: Long, value: Array[Byte], parent: Option[ServiceMapping]) extends Mapping