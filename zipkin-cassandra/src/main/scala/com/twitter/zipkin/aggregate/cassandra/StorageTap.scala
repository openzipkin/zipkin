package com.twitter.zipkin.aggregate.cassandra

import java.util.UUID

import cascading.flow.FlowProcess
import cascading.tap.hadoop.io.{HadoopTupleEntrySchemeCollector, HadoopTupleEntrySchemeIterator}
import cascading.tap.{SinkMode, Tap}
import cascading.tuple._
import org.apache.hadoop.mapred._


final class StorageTap(sinkMode: SinkMode)
  extends Tap[Config, Input, Output](new StorageScheme, sinkMode) {

  override val getIdentifier = UUID.randomUUID().toString

  override def deleteResource(conf: Config): Boolean = true
  override def resourceExists(conf: Config): Boolean = true
  override def getModifiedTime(conf: Config): Long = System.currentTimeMillis()
  override def createResource(conf: Config): Boolean = true

  override def sourceConfInit(flowProcess: FlowProcess[Config], conf: Config) {
    conf.setInputFormat(classOf[StorageInputFormat])
  }

  override def sinkConfInit(flowProcess: FlowProcess[Config], conf: Config) {
    conf.setOutputFormat(classOf[StorageOutputFormat])
  }

  override def openForWrite(flowProcess: FlowProcess[Config], output: Output): TupleEntryCollector = {
    // Scala's type system wants us to erase some type info here...
    val erased
      = this.asInstanceOf[Tap[JobConf, RecordReader[_,_], OutputCollector[_,_]]]
    new HadoopTupleEntrySchemeCollector(flowProcess, erased)
  }

  override def openForRead(flowProcess: FlowProcess[Config], input: Input): TupleEntryIterator = {
    new HadoopTupleEntrySchemeIterator(flowProcess, this, input)
  }
}
