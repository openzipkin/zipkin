package com.twitter.zipkin.aggregate.cassandra

import cascading.tap.{SinkMode, Tap}
import com.twitter.scalding._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.cassandra.CassieSpanStoreDefaults

final class SpanSource extends Source with TypedSource[Span] with TypedSink[Dependencies] {

  private def toSinkMode(readOrWrite: AccessMode) = readOrWrite match {
    case Read => SinkMode.KEEP
    case Write => SinkMode.UPDATE
  }

  override def createTap(readOrWrite: AccessMode)(implicit mode: Mode): Tap[_, _, _]
  = new StorageTap(toSinkMode(readOrWrite))


  override def converter[U >: Span]: TupleConverter[U] = TupleConverter.build(1){ tuple =>
    CassieSpanStoreDefaults.SpanCodec.decode(tuple.getObject(0).asInstanceOf[EncodedSpan]).toSpan
  }

  override def setter[U <: Dependencies]: TupleSetter[U] =
    TupleSetter.asSubSetter(TupleSetter.singleSetter[Dependencies])
}