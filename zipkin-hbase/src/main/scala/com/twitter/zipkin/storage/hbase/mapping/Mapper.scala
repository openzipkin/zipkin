package com.twitter.zipkin.storage.hbase.mapping

import com.twitter.logging.Logger
import com.twitter.util._
import com.twitter.zipkin.hbase.TableLayouts
import com.twitter.zipkin.storage.hbase.utils._
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.util.Bytes
import com.twitter.zipkin.storage.hbase.utils.HBaseTable
import com.twitter.zipkin.storage.hbase.utils.IDGenerator
import com.twitter.zipkin.util.Util
import com.twitter.util.Duration._
import scala.Some
import com.twitter.zipkin.storage.hbase.utils.HBaseTable
import com.twitter.zipkin.storage.hbase.utils.IDGenerator
import com.twitter.zipkin.storage.util.Retry

trait Mapper[T >: Null <: Mapping] {
  val log = Logger.get(getClass.getName)

  val mappingTable: HBaseTable
  val qualBytes: Array[Byte]
  val idGen: IDGenerator
  val parentId: Long
  val typeBytes: Array[Byte]


  private lazy val cache = new ConcurrentHashMap[ByteBuffer, T]()
  private lazy val pool = new ExecutorServiceFuturePool(ThreadProvider.mappingExecutor)
  private val emptyBytes: Array[Byte] = Array[Byte]()

  /**
   * All implementations need to provide their own implementation that creates
   * whichever type of mapping is being generated.
   *
   * @param id The id of the mapping.  A positive Long.
   * @param value The value that is being mapped to an id.
   * @return New Mapping object to represent id <-> value.
   */
  protected def createInternal(id: Long, value: Array[Byte]): T

  /**
   * Get the mapping for a name.
   * @param name
   * @return
   */
  def get(name: String): Future[T] = {
    val normString = name.toLowerCase
    if (normString.equals("")) {
      throw new Exception("Can't get empty string")
    }
    get(ByteBuffer.wrap(Bytes.toBytes(normString)))
  }

  def get(value: ByteBuffer): Future[T] = {
    // Check in the local cache first before using the FuturePool.  Most requests will be served from
    // cache so no need to incur the ExecutorService overhead.
    val earlyCacheOption = Option(cache.get(value)).map(Future.value)

    // Once we reach here we are pretty sure that going to HBase will be required.  So we return a wrapping
    // Future to be run on the FuturePool.  Then inside the future we'll block on all of the different ways that
    // a mapping can be created.  Blocking until getting a durable result allows us to catch errors in the retry loop
    // and present a single future to the caller.
    val result: Future[T] = pool {
      // Blocking retry loop.
      Retry(100) {
        // Try the cache again.  It's possible that some other thread has done all the work for us.
        val cachedOption: Option[Future[T]] = Option(cache.get(value)).map(Future.value)
        val mapping: Future[T] = cachedOption.getOrElse {
          // If the cache doesn't contain a mapping we'll need to get it from or put it into HBase.

          // so get the byte array for the value.
          val valueBytes: Array[Byte] = Util.getArrayFromBuffer(value)
          val fromOrToHBase: Future[T] = getFromHBase(valueBytes).flatMap { mo =>
            mo match {
              // If there is already a mapping stored in hbase
              case Some(fromHBaseMapping) => {
                // put the mapping from hbase into the cache.
                cacheMapping(fromHBaseMapping)
                // re-wrap the mapping in a future to keep types sane.
                Future.value(fromHBaseMapping)
              }
              // If there was no mapping on HBase then try and create and store a new mapping.
              case None => createProposed(valueBytes).flatMap { pm =>
                store(pm)
              }
            }
          }
          fromOrToHBase
        }
        Await.result(mapping, Duration.fromMilliseconds(500))
      }
    }
    // return the future that best answers the query.
    earlyCacheOption.getOrElse(result)
  }

  def getAll: Future[Set[T]] = {
    val maxToLoad = 100000
    val scan = new Scan()
    scan.setMaxVersions(1)
    scan.setCaching(maxToLoad)
    scan.addColumn(TableLayouts.mappingForwardFamily, qualBytes)
    /*
    Yes turn on caching for a scan.

    GASP!!!

    This should be a small table and it will greatly help with
    creating more mappings if this is in cache.
    */
    scan.setCacheBlocks(true)
    mappingTable.scan(scan, maxToLoad).map { results =>
      val mappings = results.map(createFromResult)
      mappings.foreach { m => cacheMapping(m)}
      mappings.toSeq.toSet
    }
  }

  private def getFromHBase(valueBytes: Array[Byte]): Future[Option[T]] = {
    val get = new Get(valueBytes)
    get.addColumn(TableLayouts.mappingForwardFamily, qualBytes)
    val resultFuture: Future[Seq[Result]] = mappingTable.get(Seq(get))
    resultFuture.map { results =>
      results.headOption.map(createFromResult(_))
    }
  }

  private def store(proposedMapping: T): Future[T] = {
    // Forwards : Name to Id
    // backwards: Id to Name

    val idBytes = Bytes.toBytes(proposedMapping.id)
    val valBytes = proposedMapping.value

    // Now that we have an id put the id to name mapping in hbase.
    val backwardPut = new Put(idBytes)
    backwardPut.add(TableLayouts.mappingBackwardsFamily, qualBytes, valBytes)

    val forwardPut = new Put(valBytes)
    forwardPut.add(TableLayouts.mappingForwardFamily, qualBytes, idBytes)

    // Probably need to clean up if we fail here but the ui should be fine.
    // There will be a dangling id -> name reference.  But since in order to ever use it
    // this whole method must complete, a dangling reference isn't a large deal.
    val backwardsSuccessFuture = mappingTable.checkAndPut(idBytes, TableLayouts.mappingBackwardsFamily, qualBytes, emptyBytes, backwardPut)

    // Send it to hbase.
    val forwardsSuccessFuture = backwardsSuccessFuture.flatMap { backwardsSuccess =>
    // Make sure that the id -> name mapping worked before continuing on.
    // This should never fail, but better safe than sorry.
      if (!backwardsSuccess) {
        throw new Exception("ID(" + Bytes.toLong(idBytes) + ") Already Exists. Retrying")
      }
      mappingTable.checkAndPut(valBytes, TableLayouts.mappingForwardFamily, qualBytes, emptyBytes, forwardPut)
    }

    // The boolean returned should tell us if there was a race creating the forward mapping.
    val resultingMapping: Future[T] = forwardsSuccessFuture.map { forwardsSuccess =>
      if (!forwardsSuccess) {
        throw new Exception("Mapping (" + proposedMapping + ") Already Exists. Retrying")
      }

      // At this point the mapping is durable.
      cacheMapping(proposedMapping)
      proposedMapping
    }

    resultingMapping
  }

  private def cacheMapping(mapping:T) {
    val replaced = cache.put(ByteBuffer.wrap(mapping.value), mapping)
    assert( replaced == null || replaced.id == mapping.id)
  }

  private def createProposed(value: Array[Byte]): Future[T] = {
    idGen.createNewId(parentId, typeBytes.head).map { id =>
      createInternal(id, value)
    }
  }

  private def createFromResult(result: Result): T = {
    val id = Bytes.toLong(result.getValue(TableLayouts.mappingForwardFamily, qualBytes))
    createInternal(id, result.getRow)
  }

}
