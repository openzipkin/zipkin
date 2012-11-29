package com.twitter.zipkin.cassandra

import com.twitter.util.Config
import com.twitter.zipkin.storage.Storage

case class CassandraStorageBuilder(
  keyspaceBuilder: KeyspaceBuilder,
  columnFamily: String = "Traces"
) extends Config[Storage] {

  def apply(): Storage = {

  }
}
