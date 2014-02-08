package com.twitter.zipkin.storage.hbase

import com.twitter.algebird.Semigroup
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Time, Future}
import com.twitter.zipkin.common.Dependencies
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.hbase.TableLayouts
import com.twitter.zipkin.storage.Aggregates
import com.twitter.zipkin.storage.hbase.mapping.ServiceMapper
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import org.apache.hadoop.hbase.client.{Scan, Put}
import org.apache.hadoop.hbase.util.Bytes
import scala.collection.JavaConverters._

trait HBaseAggregates extends Aggregates {

  val dependenciesTable: HBaseTable
  val topAnnotationsTable: HBaseTable

  val mappingTable: HBaseTable
  val idGenTable: HBaseTable
  lazy val idGen = new IDGenerator(idGenTable)
  lazy val serviceMapper = new ServiceMapper(mappingTable, idGen)

  val serializer = new BinaryThriftStructSerializer[gen.Dependencies] {
    def codec = gen.Dependencies
  }

  def close() {
    mappingTable.close()
    idGenTable.close()

    topAnnotationsTable.close()
    dependenciesTable.close()
  }

  def getDependencies(startDate: Option[Time], endDate: Option[Time]=None): Future[Dependencies] = {
    val scan = new Scan()
    scan.setStartRow(Bytes.toBytes(Long.MaxValue - startDate.map(_.inMilliseconds).getOrElse(Long.MaxValue)))
    endDate.foreach { ed => scan.setStopRow(Bytes.toBytes(Long.MaxValue - ed.inMilliseconds))}
    scan.addColumn(TableLayouts.dependenciesFamily, Bytes.toBytes("\0"))
    dependenciesTable.scan(scan, 100).map { results =>
      val depList = results.flatMap { result =>
        result.list().asScala.headOption.map { kv =>
          val tDep = serializer.fromBytes(kv.getValue)
          tDep.toDependencies
        }
      }
      Semigroup.sumOption(depList).get
    }
  }

  def storeDependencies(dependencies: Dependencies): Future[Unit] = {
    val rk = Bytes.toBytes(Long.MaxValue - dependencies.startTime.inMilliseconds)
    val put = new Put(rk)
    put.add(TableLayouts.dependenciesFamily, Bytes.toBytes("\0"), serializer.toBytes(dependencies.toThrift))
    dependenciesTable.put(Seq(put))
  }

  def getTopAnnotations(serviceName: String): Future[Seq[String]] = {
    getTopAnnotationInternal(serviceName, TableLayouts.topAnnotationFamily)
  }

  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]] = {
    getTopAnnotationInternal(serviceName, TableLayouts.topAnnotationKeyValueFamily)
  }

  def storeTopAnnotations(serviceName: String, annotations: Seq[String]): Future[Unit] = {
    storeTopAnnotationsInternal(serviceName, annotations, TableLayouts.topAnnotationFamily)
  }

  def storeTopKeyValueAnnotations(serviceName: String, a: Seq[String]): Future[Unit] = {
    storeTopAnnotationsInternal(serviceName, a, TableLayouts.topAnnotationKeyValueFamily)
  }

  private def getTopAnnotationInternal(serviceName: String, fam: Array[Byte]): Future[Seq[String]] = {
    serviceMapper.get(serviceName).map { serviceMapping =>
      val startRk = Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(0L)
      val scan = new Scan()
      scan.addFamily(fam)
      scan.setCaching(1)

      // This is a very small table and there will
      // be row key skew.  To get away with this the table
      // needs to stay in memory as much as possible.
      scan.setCacheBlocks(true)

      scan.setStartRow(startRk)
      scan
    }.flatMap { scan =>
      val results = topAnnotationsTable.scan(scan, 1)
      results
    }.map { results =>
      val resultOption = results.headOption
      resultOption.map { result =>
        result.list().asScala.map { kv =>
          (Bytes.toInt(kv.getQualifier), Bytes.toString(kv.getValue))
        }.sortBy {_._1}.map {_._2}
      }.getOrElse(Seq.empty[String])
    }
  }

  private def storeTopAnnotationsInternal(serviceName: String, annotations: Seq[String], fam: Array[Byte]): Future[Unit] = {
    serviceMapper.get(serviceName).map { serviceMapping =>
      val put = new Put(Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(Long.MaxValue - System.currentTimeMillis()))
      annotations.zipWithIndex.map { case (anno: String, id: Int)=>
        put.add(TableLayouts.topAnnotationFamily, Bytes.toBytes(id), Bytes.toBytes(anno))
      }
      put
    }.flatMap { put =>
      topAnnotationsTable.put(Seq(put))
    }
  }

}
