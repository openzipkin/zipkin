package com.twitter.zipkin.storage.cassandra

import com.twitter.util._
import com.twitter.zipkin.common.Dependencies
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.DependencyStore
import com.twitter.zipkin.thriftscala.{Dependencies => ThriftDependencies}
import org.twitter.zipkin.storage.cassandra.Repository
import java.util.concurrent.TimeUnit._

import scala.collection.JavaConverters._

/**
 * This implementation of DependencyStore assumes that the job aggregating dependencies
 * only writes once a day. As such calls to [[.storeDependencies]] which vary contained
 * by a day will overwrite eachother.
 */
abstract class CassandraDependencyStore extends DependencyStore {

  /** Deferred as repository eagerly creates network connections */
  protected def repository: Repository

  private[this] val pool = FuturePool.unboundedPool
  private[this] val codec = new ScroogeThriftCodec[ThriftDependencies](ThriftDependencies)

  def close() = repository.close()

  override def getDependencies(startTime: Option[Long], endTime: Option[Long] = None) = pool {
    val endMicros = endTime.getOrElse(Time.now.inMicroseconds)

    val endEpochDayMillis = floorEpochMicrosToDayMillis(endMicros)
    val startEpochDayMillis = floorEpochMicrosToDayMillis(endMicros - MICROSECONDS.convert(1, DAYS))

    val dependencies = repository.getDependencies(startEpochDayMillis, endEpochDayMillis).asScala
      .map(codec.decode(_))
      .map(thriftToDependencies(_).toDependencies)

    if (dependencies.isEmpty) {
      Dependencies.zero
    } else {
      val startMicros = dependencies.head.startTime
      val endMicros = dependencies.last.endTime
      Dependencies(startMicros, endMicros, dependencies.flatMap(_.links))
    }
  }

  def floorEpochMicrosToDayMillis(micros: Long) = {
    Time.fromMicroseconds(micros).floor(Duration.fromTimeUnit(1, DAYS)).inMilliseconds
  }

  override def storeDependencies(dependencies: Dependencies): Future[Unit] = pool {
    val thrift = codec.encode(dependenciesToThrift(dependencies).toThrift)
    repository.storeDependencies(floorEpochMicrosToDayMillis(dependencies.startTime), thrift)
  }
}
