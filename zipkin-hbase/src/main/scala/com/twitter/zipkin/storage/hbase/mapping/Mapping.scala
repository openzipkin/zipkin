package com.twitter.zipkin.storage.hbase.mapping

trait Mapping {
  val id: Long
  val value: Array[Byte]
  val parent: Option[Mapping]
}