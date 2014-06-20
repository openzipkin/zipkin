package com.twitter.zipkin.hbase

import com.twitter.conversions.time._
import com.twitter.util.Duration
import org.apache.hadoop.hbase.client.HBaseAdmin
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding
import org.apache.hadoop.hbase.io.compress.Compression
import org.apache.hadoop.hbase.regionserver.BloomType
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, HColumnDescriptor, HTableDescriptor}

/**
 * This a utility to create all of the needed tables.
 *
 * It also contains all of the table names and the column family names.
 */
object TableLayouts {

  val storageTableName = "zipkin.traces"
  val storageFamily = Bytes.toBytes("S")
  val storageTTL = 14.days

  val durationTableName = "zipkin.duration"
  val durationDurationFamily = Bytes.toBytes("D")
  val durationStartTimeFamily = Bytes.toBytes("s")

  val idxServiceTableName = "zipkin.idxService"
  val idxServiceFamily = Bytes.toBytes("D")

  val idxServiceSpanNameTableName = "zipkin.idxServiceSpanName"
  val idxServiceSpanNameFamily = Bytes.toBytes("D")

  val idxServiceAnnotationTableName = "zipkin.idxServiceAnnotation"
  val idxAnnotationFamily = Bytes.toBytes("D")

  val topAnnotationsTableName = "zipkin.topAnnotations"
  val topAnnotationFamily = Bytes.toBytes("A")
  val topAnnotationKeyValueFamily = Bytes.toBytes("K")

  val dependenciesTableName = "zipkin.dependencies"
  val dependenciesFamily = Bytes.toBytes("D")

  val mappingTableName = "zipkin.mappings"
  val mappingForwardFamily = Bytes.toBytes("F")
  val mappingBackwardsFamily = Bytes.toBytes("R")

  val idGenTableName = "zipkin.idGen"
  val idGenFamily = Bytes.toBytes("D")

  val tables = Map(
    storageTableName -> (Seq(storageFamily), Some(storageTTL)),
    durationTableName -> (Seq(durationDurationFamily, durationStartTimeFamily), Some(storageTTL)),
    idxServiceTableName -> (Seq(idxServiceFamily), Some(storageTTL)),
    idxServiceSpanNameTableName -> (Seq(idxServiceSpanNameFamily), Some(storageTTL)),
    idxServiceAnnotationTableName -> (Seq(idxAnnotationFamily), Some(storageTTL)),
    topAnnotationsTableName -> (Seq(topAnnotationFamily, topAnnotationKeyValueFamily), Some(storageTTL)),
    dependenciesTableName -> (Seq(dependenciesFamily) , Some(storageTTL)),

    // Tables that need to be kept forever.
    idGenTableName -> (Seq(idGenFamily), None),
    mappingTableName -> (Seq(mappingForwardFamily, mappingBackwardsFamily), None)
  )

  def createTables(admin:HBaseAdmin) {
    createTables(admin, tables.keys.toSeq, None)
  }

  def createTables(admin:HBaseAdmin, compression:Option[Compression.Algorithm]) {
    createTables(admin, tables.keys.toSeq, compression)
  }

  def createTables(admin: HBaseAdmin, tableNames:Seq[String], compression:Option[Compression.Algorithm]) {
    tableNames.foreach { tableName =>
      tables.get(tableName).foreach { case (families, ttl) =>
        println("Creating table: "+tableName)
        if (admin.tableExists(tableName)) {
          println("Table "+tableName+" already existed. Disabling and deleting.")
          admin.disableTable(tableName)
          admin.deleteTable(tableName)
        }
        admin.createTable(createHTD(tableName, families, ttl, compression))
        println("Table "+tableName+" created")
      }
    }
  }


  private[this] def createHTD(tableName: String,
                              families: Seq[Array[Byte]],
                              ttlOption: Option[Duration] = None,
                              compressionOption:Option[Compression.Algorithm]=None): HTableDescriptor = {
    val htd = new HTableDescriptor(tableName)
    val hcds = families.map { fam =>
      val hcd = new HColumnDescriptor(fam)
      hcd.setBloomFilterType(BloomType.ROW)
      hcd.setDataBlockEncoding(DataBlockEncoding.FAST_DIFF)
      ttlOption.foreach { ttl => hcd.setTimeToLive(ttl.inSeconds)     }
      compressionOption.foreach { algo => hcd.setCompressionType(algo) }
      hcd
    }

    hcds.foreach(htd.addFamily)
    htd
  }

  def main(args: Array[String]) {
    val conf = HBaseConfiguration.create()
    val hbaseAdmin = new HBaseAdmin(conf)

    val compression = args.headOption.map { compString =>  Compression.getCompressionAlgorithmByName(compString) }

    createTables(hbaseAdmin, compression)
  }
}
