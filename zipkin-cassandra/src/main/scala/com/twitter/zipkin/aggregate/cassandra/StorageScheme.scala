package com.twitter.zipkin.aggregate.cassandra

import cascading.flow.FlowProcess
import cascading.scheme.{Scheme, SinkCall, SourceCall}
import cascading.tap.Tap
import cascading.tuple._
import com.twitter.zipkin.common.Dependencies
import org.apache.hadoop.io.LongWritable

final class StorageScheme extends Scheme[Config, Input, Output, Data, Data]() {
  setSourceFields(Fields.ALL)
  setSinkFields(Fields.ALL)

  override def sourcePrepare(flowProcess: FlowProcess[Config], sourceCall: SourceCall[Data, Input]) {
    sourceCall.setContext(sourceCall.getInput.createKey() -> sourceCall.getInput.createValue())
  }

  override def sourceCleanup(flowProcess: FlowProcess[Config], sourceCall: SourceCall[Data, Input]) {
    sourceCall.setContext(null)
  }


  override def source(flowProcess: FlowProcess[Config], sourceCall: SourceCall[Data, Input]) = {
    val result = new Tuple
    val (key: Key,value: EncodedSpan) = sourceCall.getContext
    val hasNext = sourceCall.getInput.next(key, value)
    if(hasNext) {
      result.add(value)
      sourceCall.getIncomingEntry.setTuple(result)
    }
    hasNext
  }

  override def sink(flowProcess: FlowProcess[Config], sinkCall: SinkCall[Data, Output]) {
    val dependencies = sinkCall.getOutgoingEntry.getObject(0).asInstanceOf[Dependencies]
    sinkCall.getOutput.collect(new LongWritable(0L), dependencies)
  }

  override def sourceConfInit(flowProcess: FlowProcess[Config], tap: Tap[Config, Input, Output], conf: Config) = ()
  override def sinkConfInit(flowProcess: FlowProcess[Config], tap: Tap[Config, Input, Output], conf: Config) = ()
}
