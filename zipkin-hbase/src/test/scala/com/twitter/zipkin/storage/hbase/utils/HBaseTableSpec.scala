package com.twitter.zipkin.storage.hbase.utils

import com.twitter.util.Await
import org.apache.hadoop.hbase.client.{Get, HTable, Put}
import org.apache.hadoop.hbase.util.Bytes

class HBaseTableSpec extends HBaseSpecification {

  val tableName = "testTable"
  val family = Bytes.toBytes("D")

  doBeforeSpec {
    _util.createTable(Bytes.toBytes(tableName), family)
  }

  "HBaseTable" should {
    doBefore {
      _util.truncateTable(Bytes.toBytes(tableName))
    }
    "checkAndPut" in {
      val t1 = new HBaseTable(_conf, tableName)
      val t2 = new HBaseTable(_conf, tableName)

      val rk = Bytes.toBytes("test")
      val qual = Bytes.toBytes(0)

      val p = new Put(rk)
      p.add(family, qual, Bytes.toBytes("TESTING"))

      val t1Result = t1.checkAndPut(rk, family, qual, Array[Byte](), p)
      val t2Result = t2.checkAndPut(rk, family, qual, Array[Byte](), p)

      !Await.result(t1Result) must_== Await.result(t2Result)
    }
    "put" in {
      val t1 =  new HBaseTable(_conf, tableName)
      val p = new Put(Bytes.toBytes("hello"))
      p.add(family, Bytes.toBytes("a"), Bytes.toBytes("world."))
      Await.result(t1.put(Seq(p)))

      val ht = new HTable(_conf, tableName)
      val result = ht.get(new Get(Bytes.toBytes("hello")))
      result.size() must_== 1
    }
    "get" in {
      val ht = new HTable(_conf, tableName)
      val p = new Put(Bytes.toBytes("hello"))
      p.add(family, Bytes.toBytes("a"), Bytes.toBytes("world."))
      ht.put(p)

      val t1 =  new HBaseTable(_conf, tableName)
      val resultFuture = t1.get(Seq(new Get(Bytes.toBytes("hello"))))
      val result = Await.result(resultFuture)
      result.size must_== 1
    }
  }

}
