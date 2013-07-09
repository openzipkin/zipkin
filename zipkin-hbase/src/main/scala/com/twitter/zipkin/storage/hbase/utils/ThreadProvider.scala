package com.twitter.zipkin.storage.hbase.utils

import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent._


/**
 * Object to provide shared thread pools to the HBase storage system.
 */
object ThreadProvider {
  /**
   * Default executor used for most operations.
   */
  lazy val defaultExecutor = createExecutor(16, "HBase")

  /**
   * Storage Executor
   */
  lazy val storageExecutor = createExecutor(16, "HBase-Storage")

  /**
   * Index Service Executor
   */
  lazy val indexServiceExecutor = createExecutor(16, "HBase-Idx-Service")

  /**
   * Index Service Span Executor
   */
  lazy val indexServiceSpanExecutor = createExecutor(16, "HBase-Idx-Span")

  /**
   * Index Annotation Executor
   */
  lazy val indexAnnotationExecutor = createExecutor(16, "HBase-Idx-Annotation")

  /**
   * Mapping executor. Used by ServiceMapper et al.
   */
  lazy val mappingExecutor = createExecutor(16, "HBase-Mapping")

  /**
   * Used by the Mapping table
   */
  lazy val mappingTableExecutor = createExecutor(16, "HBase-Mapping-Table")

  /**
   * IDGen thread pool.
   */
  lazy val idGenTableExecutor = createExecutor(16, "HBase-IDGen")

  /**
   * Client executor used to power the insides of org.apache.hadoop.hbase.client.HTable
   */
  lazy val clientExecutor = createExecutor(32, "HBase-Client")

  private def createExecutor(numThreads:Int, poolName:String):ExecutorService = {
    new ThreadPoolExecutor(
      4,
      numThreads,
      60, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      new NamedPoolThreadFactory(poolName, true))
  }
}
