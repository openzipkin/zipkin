package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.util.Await
import com.twitter.zipkin.hbase.{TableLayouts}
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import com.twitter.zipkin.storage.hbase.ZipkinHBaseSpecification

class SpanNameMapperSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = Seq(
    TableLayouts.idGenTableName,
    TableLayouts.mappingTableName
  )

  "SpanNameMapper" should {
    "get" in {
      val mappingTable = new HBaseTable(_conf, TableLayouts.mappingTableName)
      val idGenTable = new HBaseTable(_conf, TableLayouts.idGenTableName)
      val idGen = new IDGenerator(idGenTable)
      val serviceMapper = new ServiceMapper(mappingTable, idGen)

      val serviceNameOne = "TestService"
      val spanNameOne = "TestSpanOne"

      val serviceMappingOne = Await.result(serviceMapper.get(serviceNameOne))
      val spanNameMappingOne = Await.result(serviceMappingOne.spanNameMapper.get(spanNameOne))
      val spanNameMappingOneAgain = Await.result(serviceMappingOne.spanNameMapper.get(spanNameOne))

      spanNameMappingOne.id must_== spanNameMappingOneAgain.id

      val names = Await.result(serviceMappingOne.spanNameMapper.getAll.map { maps => maps.map(_.name)}).toSeq
      names must contain(spanNameOne.toLowerCase)
    }

    "be independent" in {
      val mappingTable = new HBaseTable(_conf, TableLayouts.mappingTableName)
      val idGenTable = new HBaseTable(_conf, TableLayouts.idGenTableName)
      val idGen = new IDGenerator(idGenTable)
      val serviceMapper = new ServiceMapper(mappingTable, idGen)

      val serviceNameOne = "TestServiceOne"
      val serviceNameTwo = "TestServiceTwo"

      val spanNameOne = "spanNameOne"
      val spanNameTwo = "spanNameOne"
      val spanNameThree =  serviceNameTwo + ".spanNameThree"

      val serviceMappingOne = Await.result(serviceMapper.get(serviceNameOne))
      val serviceMappingTwo = Await.result(serviceMapper.get(serviceNameTwo))

      val spanNameMappingOne = Await.result(serviceMappingOne.spanNameMapper.get(spanNameOne))
      val spanNameMappingTwo = Await.result(serviceMappingTwo.spanNameMapper.get(spanNameTwo))
      val spanNameMappingThree = Await.result(serviceMappingTwo.spanNameMapper.get(spanNameThree))

      spanNameMappingOne.id must_== 1
      spanNameMappingTwo.id must_== 1
      spanNameMappingThree.id must_== 2

      val serviceOneSpanMappings = Await.result(serviceMappingOne.spanNameMapper.getAll)
      val serviceTwoSpanMappings = Await.result(serviceMappingTwo.spanNameMapper.getAll)

      // Make sure that none of the mappings from service One have a parent that points to service two
      serviceOneSpanMappings.map {_.parent.get.id} must not contain (serviceMappingTwo.id)
      // make sure that none of the mappings from service two have a parent that points to service one
      serviceTwoSpanMappings.map {_.parent.get.id} must not contain (serviceMappingOne.id)

      serviceOneSpanMappings.map { _.name } must not contain (spanNameThree)
    }
  }

}
