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
abstract class CassandraDependencyStore extends DependencyStore {

  /** Deferred as repository creates network connections */
  protected def repository: Repository

  private[this] val codec = new ScroogeThriftCodec[ThriftDependencies](ThriftDependencies)

  def close() = repository.close()

  override def getDependencies(endTs: Long, lookback: Option[Long]) = {

    val endEpochDayMillis = floorToDay(endTs)
    val startEpochDayMillis = floorToDay(endTs - lookback.getOrElse(endTs))

    FutureUtil.toFuture(repository.getDependencies(startEpochDayMillis, endEpochDayMillis))
      .map { dependencies => dependencies.asScala
          .map(codec.decode(_))
          .map(thriftToDependencies(_).toDependencies)
          .flatMap(_.links)
      }
  }

  private def floorToDay(millis: Long) = {
    Time.fromMilliseconds(millis).floor(Duration.fromTimeUnit(1, DAYS)).inMilliseconds
  }

  override def storeDependencies(dependencies: Dependencies): Future[Unit] = {
    val thrift = codec.encode(dependenciesToThrift(dependencies).toThrift)
    FutureUtil.toFuture(
      repository.storeDependencies(floorToDay(dependencies.startTs), thrift))
      .map(_ => ())
  }
}
