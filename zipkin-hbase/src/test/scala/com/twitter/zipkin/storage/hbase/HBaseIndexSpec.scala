package com.twitter.zipkin.storage.hbase

import com.twitter.util.Await
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Endpoint, Span, Annotation}
import com.twitter.zipkin.hbase.{TableLayouts, IndexBuilder}
import com.twitter.zipkin.storage.hbase.mapping.ServiceMapper
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes

class HBaseIndexSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = TableLayouts.tables.keys.toSeq

  var index: HBaseIndex = null

  val endOfTime = Long.MaxValue
  def before(ts: Long) = ts - 1
  val traceIdOne = 100
  val spanOneStart = 90000L
  val serviceNameOne = "HBase.Client"
  val endpointOne = new Endpoint(0, 0, serviceNameOne)
  val annoOneList = List(
    new Annotation(spanOneStart, Constants.ClientSend, Some(endpointOne)),
    new Annotation(spanOneStart + 100, Constants.ClientRecv, Some(endpointOne))
  )
  val spanOneId: Long = 32003
  val spanOneName = "startingSpan"
  val spanOne = new Span(traceIdOne, spanOneName, spanOneId, None, annoOneList, Seq())

  val spanTwoStart = spanOneStart + 100
  val serviceNameTwo = "HBase.RegionServer"
  val endPointTwo = new Endpoint(0, 0, serviceNameTwo)
  val annoTwoList = List(new Annotation(spanTwoStart, Constants.ServerRecv, Some(endPointTwo)))
  val spanTwo = new Span(traceIdOne, "secondSpan", 45006, Some(spanOneId), annoTwoList, Seq())

  val spanThreeStart = spanTwoStart + 100
  val annoThreeList = List(new Annotation(spanThreeStart, Constants.ServerRecv, Some(endPointTwo)))
  val spanThree = new Span(traceIdOne, "spanThree", 45007, Some(spanOneId), annoThreeList, Seq())

  val traceIdFour = 103
  val spanFourStart = spanThreeStart + 100
  val annoFourList = List(new Annotation(spanFourStart, Constants.ServerRecv, Some(endPointTwo)))
  val spanFour = new Span(traceIdFour, "spanThree", 45008, None, annoFourList, Seq())


  val spanFiveStart = spanFourStart + 100
  val annoFiveValue = "CustomANNO"
  val annoFiveList = List(new Annotation(spanFiveStart, annoFiveValue, Some(endPointTwo)))
  val spanFive = new Span(traceIdFour, "spanThree", 45009, Some(45006), annoFiveList, Seq())
  "HBaseIndex" should {

    doBefore {
      index = IndexBuilder(confOption = Some(_conf))()
    }

    "indexServiceName" in {
      val serviceTable = new HBaseTable(_conf, TableLayouts.idxServiceTableName)

      val mappingTable = new HBaseTable(_conf, TableLayouts.mappingTableName)
      val idGenTable = new HBaseTable(_conf, TableLayouts.idGenTableName)
      val idGen = new IDGenerator(idGenTable)
      val serviceMapper = new ServiceMapper(mappingTable, idGen)

      Await.result(index.indexServiceName(spanOne))
      val results = Await.result(serviceTable.scan(new Scan(), 100))
      results.size must_== 1

      val result = results.head
      result.getRow.size must_== Bytes.SIZEOF_LONG * 2

      val serviceNameFromSpan = spanOne.serviceName.get
      val serviceMapping = Await.result(serviceMapper.get(serviceNameFromSpan))
      Bytes.toLong(result.getRow) must_== serviceMapping.id
      Bytes.toLong(result.getRow.slice(Bytes.SIZEOF_LONG, Bytes.SIZEOF_LONG * 2)) must_== Long.MaxValue - spanOneStart
    }

    "indexTraceIdByServiceAndName" in {
      val serviceSpanNameTable = new HBaseTable(_conf, TableLayouts.idxServiceSpanNameTableName)
      Await.result(index.indexTraceIdByServiceAndName(spanOne))
      val scan = new Scan()
      val results = Await.result(serviceSpanNameTable.scan(scan, 100))
      results.size must_== 1

    }

    "indexSpanByAnnotations" in {
      val annoTable = new HBaseTable(_conf, TableLayouts.idxServiceAnnotationTableName)
      Await.result(index.indexSpanByAnnotations(spanFive))
      val result = Await.result(annoTable.scan(new Scan(), 1000))
      result.size must_== 1
    }

    "indexDuration" in {
      val durationTable = new HBaseTable(_conf, TableLayouts.durationTableName)
      Await.result(index.indexSpanDuration(spanOne))
      val result = Await.result(durationTable.scan(new Scan(), 1000))
      result.size must_== 1
    }

    "getTracesDuration" in {
      Await.result(index.indexSpanDuration(spanOne))
      val durations = Await.result(index.getTracesDuration(Seq(traceIdOne)))
      durations mustNotBe empty
      durations.map {_.duration} must contain(100)

      durations.map {_.traceId} must contain(traceIdOne)
    }

    "getTraceIdsByName" in {
      Await.result(index.indexServiceName(spanOne))
      Await.result(index.indexServiceName(spanTwo))
      Await.result(index.indexServiceName(spanThree))
      Await.result(index.indexServiceName(spanFour))

      Await.result(index.indexTraceIdByServiceAndName(spanOne))
      Await.result(index.indexTraceIdByServiceAndName(spanTwo))
      Await.result(index.indexTraceIdByServiceAndName(spanThree))
      Await.result(index.indexTraceIdByServiceAndName(spanFour))

      val emptyResult = Await.result(index.getTraceIdsByName(serviceNameOne, None, before(spanOneStart), 1))
      emptyResult must beEmpty

      // Try and get the first trace from the first service name
      val t1 = Await.result(index.getTraceIdsByName(serviceNameOne, None, before(endOfTime), 1))
      t1.map {_.traceId} must contain(traceIdOne)
      t1.map {_.timestamp} must contain(spanOneStart)
      t1.size must_== 1

      // Try and get the first two traces from the second service name
      val t2 = Await.result(index.getTraceIdsByName(serviceNameTwo, None, before(endOfTime), 100))
      t2.map {_.traceId} must contain(traceIdOne)
      t2.map {_.traceId} must contain(traceIdFour)
      t2.map {_.timestamp} must contain(spanTwoStart)
      t2.map {_.timestamp} must contain(spanThreeStart)

      // Try and get the first trace from the first service name and the first span name
      val t3 = Await.result(index.getTraceIdsByName(serviceNameOne, Some(spanOne.name), before(endOfTime), 1))
      t3.map {_.traceId} must contain(traceIdOne)
      t3.map {_.timestamp} must contain(spanOneStart)
      t3.size must_== 1
    }

    "getTraceIdsByAnnotation" in {
      Await.result(index.indexSpanByAnnotations(spanFive))
      val idf = index.getTraceIdsByAnnotation(spanFive.annotations.head.serviceName, spanFive.annotations.head.value, None, before(endOfTime), 100)
      val ids = Await.result(idf)
      ids.size must_== 1
      ids.map {_.traceId} must contain(spanFive.traceId)
    }

  }

}
