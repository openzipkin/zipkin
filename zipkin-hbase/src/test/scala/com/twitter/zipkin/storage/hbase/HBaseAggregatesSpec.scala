package com.twitter.zipkin.storage.hbase

import com.twitter.algebird.Moments
import com.twitter.util.{Await, Time}
import com.twitter.zipkin.common.{Dependencies, Service, DependencyLink}
import com.twitter.zipkin.hbase.{TableLayouts, AggregatesBuilder}
import com.twitter.zipkin.storage.hbase.utils.HBaseTable
import org.apache.hadoop.hbase.client.{Scan, Get}
import org.apache.hadoop.hbase.util.Bytes


class HBaseAggregatesSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = Seq(
    TableLayouts.topAnnotationsTableName,
    TableLayouts.dependenciesTableName,
    TableLayouts.idGenTableName,
    TableLayouts.mappingTableName
  )

  var aggregates: HBaseAggregates = null

  val m1 = Moments(1)
  val m2 = Moments(2)
  val d1 = DependencyLink(Service("HBase.Client"), Service("HBase.RegionServer"), m1)
  val d2 = DependencyLink(Service("HBase.Master"), Service("HBase.RegionServer"), m2)
  val deps = Dependencies(Time.fromSeconds(2), Time.fromSeconds(1000), List(d1, d2))

  val topAnnos = Seq("key1", "key2", "key3")
  val annoService = "HBase.RegionServer"

  "HBaseAggregates" should {

    doBefore {
      aggregates = AggregatesBuilder(confOption = Some(_conf))()
    }

    "storeDependencies" in {
      Await.result(aggregates.storeDependencies(deps))
      val depsTable = new HBaseTable(_conf, TableLayouts.dependenciesTableName)
      val get = new Get(Bytes.toBytes(Long.MaxValue - Time.fromSeconds(2).inMilliseconds))
      val result = Await.result(depsTable.get(Seq(get)))
      result.size must_== 1
    }

    "getDependencies" in {
      Await.result(aggregates.storeDependencies(deps))
      val retrieved = Await.result(aggregates.getDependencies(Some(Time.fromSeconds(100))))
      retrieved must_== deps
    }

    "storeTopAnnotations" in {
      Await.result(aggregates.storeTopAnnotations(annoService, topAnnos))
      val topAnnoTable = new HBaseTable(_conf, TableLayouts.topAnnotationsTableName)
      val scan = new Scan().addFamily(TableLayouts.topAnnotationFamily)
      val results = Await.result(topAnnoTable.scan(scan, 100))
      results.size must_== 1
    }

    "getTopAnnotations" in {
      Await.result(aggregates.storeTopAnnotations(annoService, topAnnos))
      val retrieved = Await.result(aggregates.getTopAnnotations(annoService))
      retrieved must_== topAnnos
    }
  }

}
