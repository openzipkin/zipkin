package com.twitter.zipkin.storage.hbase

import com.twitter.conversions.time._
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Duration, Future, FuturePool, Time}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.hbase.TableLayouts
import com.twitter.zipkin.storage.Storage
import com.twitter.zipkin.storage.hbase.utils.HBaseTable
import org.apache.hadoop.hbase.KeyValue
import org.apache.hadoop.hbase.client.{Result, Get, Put}
import org.apache.hadoop.hbase.filter.KeyOnlyFilter
import org.apache.hadoop.hbase.util.Bytes
import scala.collection.JavaConverters._

/**
 * Storage to store spans into an HBase Table. TTL is handled by HBase.
 *
 * The HBase table is laid out as follows:
 *
 * RowKey: [ TraceId ]
 * Column Family: D
 * Column Qualifier: [ SpanId ][ Hash of Annotations ]
 * Column Value: Thrift Serialized Span
 */
trait HBaseStorage extends Storage {

  val hbaseTable: HBaseTable

  val serializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  /**
   * Close the storage
   */
  def close(deadline: Time): Future[Unit] = FuturePool.unboundedPool {
    hbaseTable.close()
  }

  /**
   * Store the span in the underlying storage for later retrieval.
   * @return a future for the operation
   */
  def storeSpan(span: Span): Future[Unit] = {
    val rk = rowKeyFromSpan(span)
    val p = new Put(rk)
    val qual =  Bytes.toBytes(span.id) ++ Bytes.toBytes(span.annotations.hashCode())
    p.add(TableLayouts.storageFamily, qual, serializer.toBytes(span.toThrift))
    hbaseTable.put(Seq(p))
  }

  /**
   * Set the ttl of a trace. Used to store a particular trace longer than the
   * default. It must be oh so interesting!
   *
   * This is a NO-OP for HBase. when the data is initially put into HBase the ttl starts from the
   * timestamp. See http://hbase.apache.org/book.html#ttl for more information about HBase's TTL Data model.
   */
  def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = Future.Unit

  /**
   * Get the time to live for a specific trace.
   * If there are multiple ttl entries for one trace, pick the lowest one.
   */
  def getTimeToLive(traceId: Long): Future[Duration] = Future.value(7.days)

  def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] = {
    val gets = traceIds.map(createTraceExistsGet)
    val futures: Future[Seq[Result]] = hbaseTable.get(gets)
    futures.map { results =>
      results.map(traceExistsResultToTraceId).toSet
    }
  }

  private[this] def createTraceExistsGet(traceId: Long): Get = {
    val g = new Get(Bytes.toBytes(traceId))
    g.addFamily(TableLayouts.storageFamily)
    g.setFilter(new KeyOnlyFilter())
    g
  }

  private[this] def traceExistsResultToTraceId(result: Result): Long = {
    traceIdFromRowKey(result.getRow)
  }

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   */
  def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] = {
    hbaseTable.get(createTraceGets(traceIds)).map { rl =>
      rl.map { result =>
        val spans = resultToSpans(Option(result)).sortBy { span => getTimeStamp(span)}
        spans
      }
    }
  }

  def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = {
    val gets = createTraceGets(List(traceId))
    hbaseTable.get(gets).map { rl =>
      resultToSpans(rl.headOption).sortBy { span => getTimeStamp(span)}
    }
  }

  /**
   * This creates an HBase Get request for a Seq of traces.
   * @param traceIds All of the traceId's that are requested.
   * @return Seq of Get Requests.
   */
  private[this] def createTraceGets(traceIds: Seq[Long]): Seq[Get] = {
    traceIds.map { id =>
      val g = new Get(Bytes.toBytes(id))
      g.setMaxVersions(1)
      g.addFamily(TableLayouts.storageFamily)
    }
  }

  private[this] def resultToSpans(option: Option[Result]): Seq[Span] = {
    val lists: Seq[KeyValue] = option match {
      case Some(result) => result.list().asScala
      case None => Seq.empty[KeyValue]
    }

    val spans: Seq[Span] = lists.map { kv =>
      serializer.fromBytes(kv.getValue).toSpan
    }
    spans
  }

  /**
   * How long do we store the data before we delete it? In seconds.
   */
  def getDataTimeToLive: Int = TableLayouts.storageTTL.inSeconds

  private[this] def traceIdFromRowKey(bytes: Array[Byte]): Long = Bytes.toLong(bytes)

  private[this] def rowKeyFromSpan(span: Span): Array[Byte] = Bytes.toBytes(span.traceId)
}
