package com.twitter.zipkin.storage.hbase.utils

import com.twitter.util.{FuturePool, ExecutorServiceFuturePool, Future}
import java.util.concurrent.{Executors, ExecutorService}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.PoolMap.PoolType
import scala.collection.JavaConverters._
import com.twitter.logging.Logger

/**
 * Class to expose non-blocking version of HBase's HTable.
 *
 * All operations are non-blocking by using a thread pool.  Any empty results are filtered out before being returned.
 * @param conf Hadoop conf with HBase variables inside
 * @param tableName The name of the table to target.
 * @param mainExecutor The thread pool executor.
 * @param clientExecutor The thread pool executor.
 */
case class HBaseTable(
  conf: Configuration,
  tableName:String,
  mainExecutor: ExecutorService = ThreadProvider.defaultExecutor,
  clientExecutor: ExecutorService = ThreadProvider.clientExecutor
) {

  /**
   * Thread pool for futures.
   */
  private val pool: FuturePool = new ExecutorServiceFuturePool(mainExecutor)

  private val htablePool = new HTablePool(conf, 32,
                                          new HTableFactoryWithExecutor(clientExecutor),
                                          PoolType.Reusable)

  val log = Logger.get(getClass.getName)

  /**
   * Execute a Put async and return void when done.
   * @param puts Seq of HBase puts to send to regionserver.
   * @return Future[Unit]
   */
  def put(puts:Seq[Put]):Future[Unit] = pool {
    val htable = htablePool.getTable(tableName)
    try {
      htable.put(puts.asJava)
      htable.flushCommits()
    } catch {
      case e:Exception => log.debug("Error Putting to HBase")
    } finally {
      htable.close()
    }
  }

  /**
   * Perform a given scan.  It will grab the number of results asked for
   * @param scan Scan to execute against HBase.
   * @param numRows Number of rows to ask for.
   * @return
   */
  def scan(scan:Scan, numRows:Int):Future[Seq[Result]] = pool {
    val htable = htablePool.getTable(tableName)
    var resultScanner:ResultScanner = null
    try {
      resultScanner = htable.getScanner(scan)
      resultScanner.next(numRows).toSeq.filter { _.size > 0}.slice(0,numRows)
    } finally {
      if (resultScanner != null) {
        resultScanner.close()
      }
      htable.close()
    }
  }

  /**
   * Execute several gets returning results.
   * @param gets list of gets to pass to HBase.
   * @return Results that are returned from HBase.
   */
  def get(gets:Seq[Get]):Future[Seq[Result]] = pool {
    val htable = htablePool.getTable(tableName)
    try {
      htable.get(gets.asJava).map { r => Option(r) }.flatten.toSeq.filter { _.size > 0}
    } finally {
      htable.close()
    }
  }

  def checkAndPut(rk:Array[Byte],
                  fam:Array[Byte],
                  qaul:Array[Byte],
                  value:Array[Byte],
                  put:Put):Future[Boolean] = pool {
    val htable = htablePool.getTable(tableName)
    try {


      val result = htable.checkAndPut(rk, fam, qaul, value, put)
      result
    } catch {
      case e:Exception =>
        log.debug("Error: while check and put., %s", e)
        throw e
    } finally {
      htable.close()
    }
  }

  /**
   * Atomic increment.
   *
   * @param incr the increment to execute.
   * @return result from incrementing.
   */
  def atomicIncrement(incr:Increment):Future[Result] = pool {
    val htable = htablePool.getTable(tableName)
    try {
      htable.increment(incr)
    } finally {
      htable.close()
    }
  }

  def close() {
    this.synchronized {
      htablePool.close()
    }
  }
}
