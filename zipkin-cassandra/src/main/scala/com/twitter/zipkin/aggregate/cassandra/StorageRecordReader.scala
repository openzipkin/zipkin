package com.twitter.zipkin.aggregate.cassandra

import java.io.Closeable
import java.nio.ByteBuffer

import com.twitter.cassie.Order
import com.twitter.cassie.util.ByteBufferUtil._
import com.twitter.util.Await
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.cassandra.{CassandraStorage, CassieSpanStoreDefaults}
import org.apache.cassandra.finagle.thrift
import org.apache.cassandra.finagle.thrift.{ColumnParent, ConsistencyLevel, KeyRange, KeySlice}
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.mapred.{JobConf, Reporter}

import scala.collection.JavaConverters._

final class StorageRecordReader(inputSplit: StorageInputSplit, jobConf: JobConf, reporter: Reporter)
  extends Input()
  with Closeable {

  private var isConnectionOpen = false
  private var pos = 0
  def batchSize = 4096

  private val readEverythingSlicePredicate = {
    val startBytes = EMPTY
    val endBytes = EMPTY
    val pred = new thrift.SlicePredicate()
    pred.setSlice_range(new thrift.SliceRange(startBytes, endBytes, Order.Normal.reversed, Int.MaxValue))
  }

  private lazy val storage = {
    isConnectionOpen = true
    HadoopStorage.cassandraStoreBuilder(jobConf).storageBuilder.apply().asInstanceOf[CassandraStorage]
  }

  private lazy val rows = Await.result(storage.keyspace.provider.map { client =>
    client.get_range_slices(
      new ColumnParent(storage.traces.name),
      readEverythingSlicePredicate,
      new KeyRange(batchSize)
        .setStart_token(inputSplit.startToken)
        .setEnd_token(inputSplit.endToken),
      ConsistencyLevel.ONE
    )
  }).asScala

  private lazy val spans : Seq[EncodedSpan] = (rows map { row: KeySlice =>
    row.columns.asScala.map { cosc =>
      val encodedSpan = cosc.column.value
      encodedSpan
    }
  }).flatten

  override def next(key: Key, value: EncodedSpan): Boolean = {
    if(pos < spans.length) {
      key.set(CassieSpanStoreDefaults.SpanCodec.decode(spans(pos)).toSpan.id)

      value.clear()
      value.put(spans(pos))
      value.flip()

      pos += 1
      true
    } else {
      false
    }
  }

  override def getProgress: Float = pos.toFloat / batchSize.toFloat

  override def getPos: Long = pos.toLong

  override def createKey(): Key = new LongWritable()

  private val valueBufferSize = 8192

  override def createValue(): EncodedSpan = {
    ByteBuffer.wrap(new Array(valueBufferSize))
  }

  def close() {
    if(isConnectionOpen) {
      isConnectionOpen = false
      storage.close()
    }
  }
}