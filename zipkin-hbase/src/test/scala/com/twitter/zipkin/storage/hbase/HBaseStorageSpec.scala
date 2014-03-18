package com.twitter.zipkin.storage.hbase

import com.twitter.util.Await
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.hbase.{TableLayouts, StorageBuilder}
import org.apache.hadoop.hbase.client.{Get, HTable}
import org.apache.hadoop.hbase.util.Bytes

/**
 * This isn't really a great unit test but it's a good starting
 * point until I have a mock HBaseTable.
 */
class HBaseStorageSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = Seq(
    TableLayouts.storageTableName
  )

  val traceId = 100L
  val spanId = 567L
  val span = Span(traceId, "span.methodCall()", spanId, None, List(), List())

  var hbaseStorage: HBaseStorage = null

  "HBaseStorage" should {

    doBefore {
      hbaseStorage = StorageBuilder(confOption = Some(_conf))()
    }

    doAfter {
      hbaseStorage.close()
      hbaseStorage = null
    }

    "storeSpan" in {
      Await.result(hbaseStorage.storeSpan(span))
      // The data should be there by now.
      val htable = new HTable(_conf, TableLayouts.storageTableName)
      val result = htable.get(new Get(Bytes.toBytes(traceId)))
      result.size mustEqual 1
    }

    "tracesExist" in {
      // Put the span just in case the ordering changes.
      Await.result(hbaseStorage.storeSpan(span))
      val idsFound = Await.result(hbaseStorage.tracesExist(Seq(traceId, 3002L)))
      idsFound must contain(traceId)
      idsFound.size mustEqual 1
    }

    "getSpansByTraceId" in {
      Await.result(hbaseStorage.storeSpan(span))
      val spansFound = hbaseStorage.getSpansByTraceId(traceId)
      Await.result(spansFound) must contain(span)
    }

    "getSpansByTraceIds" in {
      Await.result(hbaseStorage.storeSpan(span))
      val spansFoundFuture = hbaseStorage.getSpansByTraceIds(Seq(traceId, 302L))
      val spansFound = Await.result(spansFoundFuture).flatten
      spansFound must contain(span)
      spansFound.size must_== 1
    }
  }
}
