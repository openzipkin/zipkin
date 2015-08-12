package com.twitter.zipkin.aggregate.cassandra

import com.twitter.zipkin
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.storage.Store
import org.apache.hadoop.mapred.JobConf
import scala.collection.JavaConverters._

object HadoopStorage {
  def cassandraStoreBuilder(jobConf: JobConf) : Store.Builder = {

    val nodes = jobConf.getStringCollection("hosts").asScala.toSet
    val port = jobConf.getInt("port", -1)

    val keyspaceBuilder = zipkin.cassandra.Keyspace.static(
      nodes = nodes,
      port = port)
    val storageWithIndexBuilder = cassandra.StorageWithIndexBuilder(keyspaceBuilder)
    Store.Builder(
      storageWithIndexBuilder,
      storageWithIndexBuilder,
      cassandra.AggregatesBuilder(keyspaceBuilder)
    )
  }
}
