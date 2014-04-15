package com.twitter.zipkin.storage

import com.twitter.logging.Logger
import com.twitter.util.Time
import com.twitter.zipkin.common.Span
import org.apache.hadoop.hbase.util.Bytes

package object hbase {

  val log = Logger.get(getClass.getName)

  /**
   * From a span get the starting timestamp.
   * @param span
   * @return
   */
  def getTimeStampRowKeyBytes(span: Span): Array[Byte] = {
    val ts = getTimeStamp(span).getOrElse {
      log.debug("Could not get timeStamp for %s", span)
      Time.now.inMicroseconds
    }
    timeStampToRowKeyBytes(ts)
  }

  def getTimeStamp(span: Span): Option[Long] = {
    val timeStamps = span.annotations.map {_.timestamp}.sortWith(_ < _)
    timeStamps.headOption
  }

  def timeStampToRowKeyBytes(timeStamp: Long): Array[Byte] = Bytes.toBytes(Long.MaxValue - timeStamp)

  def getEndScanTimeStampRowKeyBytes(ts: Long) = Bytes.toBytes(scala.math.max(Long.MaxValue - ts, 0L))
}
