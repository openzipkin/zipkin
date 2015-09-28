package com.twitter.zipkin.storage.cassandra

import com.twitter.util._
import com.twitter.zipkin.common.Dependencies
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.DependencyStore
import com.twitter.zipkin.thriftscala.{Dependencies => ThriftDependencies}
import com.twitter.zipkin.util.FutureUtil
import org.twitter.zipkin.storage.cassandra.Repository
import java.util.concurrent.TimeUnit._

import scala.collection.JavaConverters._

/**
 * This implementation of DependencyStore assumes that the job aggregating dependencies
 * only writes once a day. As such calls to [[.storeDependencies]] which vary contained
 * by a day will overwrite eachother.
 */
class CassandraDependencyStore(repository: Repository) extends DependencyStore {

  private[this] val codec = new ScroogeThriftCodec[ThriftDependencies](ThriftDependencies)

  def close() = repository.close()

  override def getDependencies(startTime: Option[Long], endTime: Option[Long] = None) = {
    val endMicros = endTime.getOrElse(Time.now.inMicroseconds)

    val endEpochDayMillis = floorEpochMicrosToDayMillis(endMicros)
    val startEpochDayMillis = floorEpochMicrosToDayMillis(endMicros - MICROSECONDS.convert(1, DAYS))

    val dependenciesFuture =
      FutureUtil.toFuture(repository.getDependencies(startEpochDayMillis, endEpochDayMillis))
        .map { dependencies =>
          dependencies.asScala
            .map(codec.decode(_))
            .map(thriftToDependencies(_).toDependencies)
        }

    dependenciesFuture.map { dependencies =>
      if (dependencies.isEmpty) {
        Dependencies.zero
      } else {
        val startMicros = dependencies.head.startTime
        val endMicros = dependencies.last.endTime
        Dependencies(startMicros, endMicros, dependencies.flatMap(_.links))
      }
    }
  }

  def floorEpochMicrosToDayMillis(micros: Long) = {
    Time.fromMicroseconds(micros).floor(Duration.fromTimeUnit(1, DAYS)).inMilliseconds
  }

  override def storeDependencies(dependencies: Dependencies): Future[Unit] = {
    val thrift = codec.encode(dependenciesToThrift(dependencies).toThrift)
    FutureUtil.toFuture(
      repository.storeDependencies(floorEpochMicrosToDayMillis(dependencies.startTime), thrift))
      .map(_ => ())
  }
}
