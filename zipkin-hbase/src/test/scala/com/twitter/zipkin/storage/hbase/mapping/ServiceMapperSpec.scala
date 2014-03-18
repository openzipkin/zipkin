package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.util.Await
import com.twitter.zipkin.hbase.{TableLayouts}
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.util.Bytes
import java.nio.ByteBuffer
import com.twitter.zipkin.storage.hbase.ZipkinHBaseSpecification

class ServiceMapperSpec extends ZipkinHBaseSpecification {

  val tablesNeeded = Seq(
    TableLayouts.idGenTableName,
    TableLayouts.mappingTableName
  )

  val namePrefix = "mapping-"
  val names = (0 to 30) map { i => namePrefix + i}

  "ServiceMapperSpec" should {

    doBefore {
      _util.truncateTable(Bytes.toBytes(TableLayouts.idGenTableName))
      _util.truncateTable(Bytes.toBytes(TableLayouts.mappingTableName))
    }

    "get" in {
      val mappingTable = new HBaseTable(_conf, TableLayouts.mappingTableName)
      val idGenTable = new HBaseTable(_conf, TableLayouts.idGenTableName)
      val idGen = new IDGenerator(idGenTable)
      val serviceMapper = new ServiceMapper(mappingTable, idGen)
      val serviceMapperTwo = new ServiceMapper(mappingTable, idGen)


      Await.result(serviceMapper.get("test")).id must_== Await.result(serviceMapperTwo.get("test")).id
      Await.result(serviceMapper.get("test")).id must_== Await.result(serviceMapper.get("test")).id
      Await.result(serviceMapperTwo.get("test")).id must_== Await.result(serviceMapperTwo.get("test")).id

      ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value) must_== ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value)
      ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value) must_== ByteBuffer.wrap(Await.result(serviceMapper.get("test")).value)
      ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value) must_== ByteBuffer.wrap(Await.result(serviceMapperTwo.get("test")).value)
    }
  }

}
