package com.twitter.zipkin.aggregate.cassandra

import com.twitter.zipkin.common.Dependencies
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.mapred.{JobConf, OutputFormat}
import org.apache.hadoop.util.Progressable

final class StorageOutputFormat extends OutputFormat[Key, Dependencies] {
  override def getRecordWriter(p1: FileSystem, conf: JobConf, p3: String, p4: Progressable)
    = new StorageRecordWriter(conf)

  override def checkOutputSpecs(fileSystem: FileSystem, jobConf: JobConf) = ()
}