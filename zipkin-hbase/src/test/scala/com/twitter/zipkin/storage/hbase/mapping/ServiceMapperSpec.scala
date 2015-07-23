package com.twitter.zipkin.storage.hbase.mapping

import java.nio.ByteBuffer

import com.twitter.util.Await
import com.twitter.zipkin.hbase.TableLayouts
import com.twitter.zipkin.storage.hbase.ZipkinHBaseSpecification
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}

class ServiceMapperSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = Seq(
    TableLayouts.idGenTableName,
    TableLayouts.mappingTableName
  )

  val namePrefix = "mapping-"
  val names = (0 to 30) map { i => namePrefix + i}

  test("get") {
    val mappingTable = new HBaseTable(_conf, TableLayouts.mappingTableName)
    val idGenTable = new HBaseTable(_conf, TableLayouts.idGenTableName)
    val idGen = new IDGenerator(idGenTable)
    val serviceMapper = new ServiceMapper(mappingTable, idGen)
    val serviceMapperTwo = new ServiceMapper(mappingTable, idGen)


    Await.result(serviceMapper.get("test")).id should be (Await.result(serviceMapperTwo.get("test")).id)
    Await.result(serviceMapper.get("test")).id should be (Await.result(serviceMapper.get("test")).id)
    Await.result(serviceMapperTwo.get("test")).id should be (Await.result(serviceMapperTwo.get("test")).id)

    ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value) should be (ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value))
    ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value) should be (ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value))
    ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value) should be (ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value))
  }
}
