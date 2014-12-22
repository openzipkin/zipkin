package com.twitter.zipkin.aggregate.cassandra

import com.twitter.util.{Await, Duration}
import com.twitter.zipkin.common.Dependencies
import org.apache.hadoop.mapred.{JobConf, RecordWriter, Reporter}

class StorageRecordWriter(conf: JobConf) extends RecordWriter[Key,Dependencies] {
  private var isConnectionOpen = false
  lazy val aggregate = {
    isConnectionOpen = true
    HadoopStorage.cassandraStoreBuilder(conf).aggregatesBuilder.apply()
  }
  override def write(key: Key, value: Dependencies) {
    println(value)
    Await.result(aggregate.storeDependencies(value), Duration.Top)
    println("Stored dependencies.")
  }

  override def close(reporter: Reporter) {
    if(isConnectionOpen) {
      isConnectionOpen = false
      aggregate.close()
    }
  }
}
