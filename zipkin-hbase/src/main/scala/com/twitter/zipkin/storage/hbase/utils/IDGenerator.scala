package com.twitter.zipkin.storage.hbase.utils

import com.twitter.util.Future
import com.twitter.zipkin.hbase.TableLayouts
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.util.Bytes

case class IDGenerator(idGenTable:HBaseTable) {
  def createNewId(parent:Long, idType:Byte):Future[Long] = {
    // Try and get a new ID.
    val inc = new Increment(Bytes.toBytes(parent))
    val qual =  new Array[Byte](idType)
    inc.addColumn(TableLayouts.idGenFamily, qual, 1L)
    // Failing here is fine.  We just waste an id.
    val idResult = idGenTable.atomicIncrement(inc)
    idResult.map { r =>
      val bytes = r.getValue(TableLayouts.idGenFamily, qual)
      if (Bytes.toLong(bytes) == Long.MaxValue) {
        throw new Exception("All ID's are used up.")
      }
      Bytes.toLong(bytes)
    }
  }
}
