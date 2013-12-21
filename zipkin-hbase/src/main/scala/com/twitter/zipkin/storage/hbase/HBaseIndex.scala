package com.twitter.zipkin.storage.hbase

import com.twitter.logging.Logger
import com.twitter.util.{Future, FuturePool, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.hbase.TableLayouts
import com.twitter.zipkin.storage.hbase.mapping.ServiceMapper
import com.twitter.zipkin.storage.hbase.utils.{HBaseTable, IDGenerator}
import com.twitter.zipkin.storage.{IndexedTraceId, TraceIdDuration, Index}
import java.nio.ByteBuffer
import org.apache.hadoop.hbase.client.{Get, Result, Scan, Put}
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.{BinaryComparator, ValueFilter}
import org.apache.hadoop.hbase.util.Bytes
import scala.collection.JavaConverters._
import scala.collection.Set
import com.twitter.zipkin.util.Util

trait HBaseIndex extends Index {
  val log = Logger.get(getClass.getName)

  val durationTable: HBaseTable
  val idxServiceTable: HBaseTable
  val idxServiceSpanNameTable: HBaseTable
  val idxServiceAnnotationTable: HBaseTable

  val mappingTable: HBaseTable
  val idGenTable: HBaseTable
  lazy val idGen = new IDGenerator(idGenTable)
  lazy val serviceMapper    = new ServiceMapper(mappingTable, idGen)

  /**
   * Close the index
   */
  def close(deadline: Time): Future[Unit] = FuturePool.unboundedPool {
    idGenTable.close()
    mappingTable.close()

    //ttl tables.
    durationTable.close()
    idxServiceTable.close()
    idxServiceAnnotationTable.close()
    idxServiceSpanNameTable.close()
  }


  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByName(serviceName: String, spanNameOption: Option[String], endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    val resultsFuture = spanNameOption match {
      case None       => getTraceIdsByNameNoSpanName(serviceName, endTs, limit)
      case Some(spanName) => getTraceIdsByNameWithSpanName(serviceName, spanName, endTs, limit)
    }

    resultsFuture.map { results =>
      results.flatMap { result => indexResultToTraceId(result) }.toSeq.distinct.take(limit)
    }
  }

  /**
   * Get the trace ids for this annotation between the two timestamps. If value is also passed we expect
   * both the annotation key and value to be present in index for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  def getTraceIdsByAnnotation(serviceName: String, annotation: String, value: Option[ByteBuffer], endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] = {
    val serviceMappingFuture = serviceMapper.get(serviceName)
    val annoMappingFuture = serviceMappingFuture.flatMap { serviceMapping =>
      serviceMapping.annotationMapper.get(annotation)
    }

    annoMappingFuture.flatMap { annoMapping =>
      val scan = new Scan()
      val startRk = Bytes.toBytes(annoMapping.parent.get.id) ++ Bytes.toBytes(annoMapping.id) ++ Bytes.toBytes(0L)
      val endRk = Bytes.toBytes(annoMapping.parent.get.id) ++ Bytes.toBytes(annoMapping.id) ++ getEndScanTimeStampRowKeyBytes(endTs)
      scan.setStartRow(startRk)
      scan.setStopRow(endRk)
      scan.addFamily(TableLayouts.idxAnnotationFamily)
      value.foreach { bb => scan.setFilter(new ValueFilter(CompareOp.EQUAL, new BinaryComparator(bb.array()))) }
      idxServiceAnnotationTable.scan(scan, limit).map { results =>
        results.flatMap { result => indexResultToTraceId(result)}.toSeq.distinct.take(limit)
      }
    }
  }

  /**
   * Fetch the duration or an estimate thereof from the traces.
   * Duration returned in micro seconds.
   */
  def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] = {
    val gets = traceIds.map { traceId =>
      val get = new Get(Bytes.toBytes(traceId))
      get.setMaxVersions(1)
    }
    // Go to hbase to get all of the durations.
    durationTable.get(gets).map { results => results.map(durationResultToDuration).toSet.toSeq  }
  }

  /**
   * Get all the service names for as far back as the ttl allows.
   */
  def getServiceNames: Future[Set[String]] = serviceMapper.getAll.map { f => f.map(_.name) }

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   */
  def getSpanNames(service: String): Future[Set[String]] = {
    // From the service get the spanNameMapper.  Then get all the maps.
    val spanNameMappingsFuture = serviceMapper.get(service).flatMap { _.spanNameMapper.getAll }
    // get the names from the mappings.
    spanNameMappingsFuture.map { maps => maps.map { _.name} }
  }

  /**
   * Index a trace id on the service and name of a specific Span
   */
  def indexTraceIdByServiceAndName(span: Span): Future[Unit] = {
    // Get the id of services and span names
    val serviceMappingsFuture =  Future.collect( span.serviceNames.map { sn =>
      serviceMapper.get(sn)
    }.toSeq)

    // Figure out when this happened.
    val timeBytes = getTimeStampRowKeyBytes(span)

    val traceIdBytes = Bytes.toBytes(span.traceId)
    val putsFuture = serviceMappingsFuture.flatMap { serviceMappings =>
      Future.collect(serviceMappings.map { serviceMapping =>
        val putF: Future[Put] = serviceMapping.spanNameMapper.get(span.name).map { spanNameMapping =>
          val rk = Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(spanNameMapping.id) ++ timeBytes
          val p = new Put(rk)
          p.add(TableLayouts.idxServiceSpanNameFamily, traceIdBytes, Bytes.toBytes(true))
          p
        }
        putF
      })
    }

    // Put the data into hbase.
    putsFuture.flatMap { puts => idxServiceSpanNameTable.put(puts) }
  }

  /**
   * Index the span by the annotations attached
   */
  def indexSpanByAnnotations(span: Span): Future[Unit] = {
    // Get the normal annotations
    val annoFutures = span.annotations.filter { a =>
    // skip core annotations since that query can be done by service name/span name anyway 5
      !Constants.CoreAnnotations.contains(a.value)
    }.map { a =>
      val sf = serviceMapper.get(a.serviceName)
      sf.flatMap { service => service.annotationMapper.get(a.value)}.map { am => (am, Bytes.toBytes(true))}
    }

    // Get the binary annotations.
    val baFutures = span.binaryAnnotations.map { ba =>
      ba.host match {
        case Some(host) => Some((ba, host))
        case None => None
      }
    }.flatten.map { case (ba, host) =>
      val sf = serviceMapper.get(host.serviceName)
      sf.flatMap { service =>
        service.annotationMapper.get(ba.key)
      }.map { am =>
        val bytes = Util.getArrayFromBuffer(ba.value)
        (am, bytes)
      }
    }

    // Store the sortable time stamp byte array.  This will be used for rk creation.
    val tsBytes = getTimeStampRowKeyBytes(span)
    val putsFuture = (baFutures ++ annoFutures).map { annoF =>
      annoF.map { case (anno, bytes) =>
        // Pulling out the parent here is safe because the parent must be set to find it here.
        val rk = Bytes.toBytes(anno.parent.get.id) ++ Bytes.toBytes(anno.id) ++ tsBytes
        val put = new Put(rk)
        put.add(TableLayouts.idxAnnotationFamily, Bytes.toBytes(span.traceId), bytes)
        put
      }
    }

    // Now put them into the table.
    Future.collect(putsFuture).flatMap { puts => idxServiceAnnotationTable.put(puts)  }
  }

  /**
   * Store the service name, so that we easily can
   * find out which services have been called from now and back to the ttl
   */
  def indexServiceName(span: Span): Future[Unit] = {
    val futureMappings = Future.collect(span.serviceNames.map { sn => serviceMapper.get(sn)}.toSeq)
    val timeBytes = getTimeStampRowKeyBytes(span)
    val putsFuture = futureMappings.map { mappings =>
      mappings.map { map =>
        val rk = Bytes.toBytes(map.id) ++ timeBytes
        val put = new Put(rk)
        put.add(TableLayouts.idxServiceFamily, Bytes.toBytes(span.traceId), Bytes.toBytes(true))
        put
      }
    }
    putsFuture.flatMap { puts => idxServiceTable.put(puts) }
  }

  /**
   * Index the span name on the service name. This is so we
   * can get a list of span names when given a service name.
   * Mainly for UI purposes
   */
  def indexSpanNameByService(span: Span): Future[Unit] = {
    val serviceMappingsFuture = span.serviceNames.map { sn => serviceMapper.get(sn)}.toSeq
    Future.collect(serviceMappingsFuture.map { smf =>
      smf.flatMap {_.spanNameMapper.get(span.name)}
    }).flatMap {
      snm => Future.Unit
    }
  }

  /**
   * Index a span's duration. This is so we can look up the trace duration.
   */
  def indexSpanDuration(span: Span): Future[Unit] = {
    val durationOption = span.duration
    val tsOption = getTimeStamp(span)
    val putOption = (durationOption, tsOption) match {
      case (Some(duration), Some(timestamp)) => Option({
        val put = new Put(Bytes.toBytes(span.traceId))
        put.add(TableLayouts.durationDurationFamily, Bytes.toBytes(span.id), Bytes.toBytes(duration))
        put.add(TableLayouts.durationStartTimeFamily, Bytes.toBytes(span.id), Bytes.toBytes(timestamp))
        put
      })
      case _ => None
    }
    putOption.map { put => durationTable.put(Seq(put)) }.getOrElse(Future.Unit)
  }

  //
  // Internal Helper Methods.
  //

  private def indexResultToTraceId(result: Result): Seq[IndexedTraceId] = {
    val rowLen = result.getRow.length
    val tsBytes = result.getRow.slice(rowLen - Bytes.SIZEOF_LONG, rowLen)
    val ts = Long.MaxValue - Bytes.toLong(tsBytes)
    result.list().asScala.map { kv =>
      IndexedTraceId(Bytes.toLong(kv.getQualifier), ts)
    }
  }

  private def getTraceIdsByNameNoSpanName(serviceName: String, endTs: Long, limit: Int): Future[Seq[Result]] = {
    val serviceMappingFuture = serviceMapper.get(serviceName)
    serviceMappingFuture.flatMap { serviceMapping =>

      val scan = new Scan()
      // Ask for more rows because there can be large number of dupes.
      scan.setCaching(limit * 10)

      val startRk = Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(0L)
      val endRk =  Bytes.toBytes(serviceMapping.id) ++ getEndScanTimeStampRowKeyBytes(endTs)
      scan.setStartRow(startRk)
      scan.setStopRow(endRk)
      // TODO(eclark): make this go back to the region server multiple times with a smart filter.
      idxServiceTable.scan(scan, limit*10)
    }
  }

  private def getTraceIdsByNameWithSpanName(serviceName: String, spanName: String, endTs: Long, limit: Int): Future[Seq[Result]] = {
    val serviceMappingFuture = serviceMapper.get(serviceName)
    serviceMappingFuture.flatMap { serviceMapping =>
      val spanNameMappingFuture = serviceMapping.spanNameMapper.get(spanName)
      spanNameMappingFuture.flatMap { spanNameMapping =>
        val scan = new Scan()
        val startRow = Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(spanNameMapping.id) ++ Bytes.toBytes(0L)
        val stopRow = Bytes.toBytes(serviceMapping.id) ++ Bytes.toBytes(spanNameMapping.id) ++ getEndScanTimeStampRowKeyBytes(endTs)
        scan.setStartRow(startRow)
        scan.setStopRow(stopRow)
        idxServiceSpanNameTable.scan(scan, limit)
      }
    }
  }

  private def durationResultToDuration(result: Result): TraceIdDuration = {
    val traceId = Bytes.toLong(result.getRow)
    val durationMap = result.getFamilyMap(TableLayouts.durationDurationFamily).asScala
    val startMap = result.getFamilyMap(TableLayouts.durationStartTimeFamily).asScala

    val duration = durationMap.map { case (qual, value) => Bytes.toLong(value)}.sum
    val start = startMap.map { case (qual, value) => Bytes.toLong(value)}.min

    TraceIdDuration(traceId, duration, start)
  }
}
