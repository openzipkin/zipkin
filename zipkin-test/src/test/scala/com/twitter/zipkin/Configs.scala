package com.twitter.zipkin

import com.twitter.logging.config._
import com.twitter.logging.LoggerFactory
import com.twitter.ostrich.admin._
import com.twitter.zipkin.cassandra.{Keyspace, StorageBuilder, IndexBuilder, AggregatesBuilder}
import com.twitter.zipkin.collector.sampler.{EverythingGlobalSampler, GlobalSampler}
import com.twitter.zipkin.config.sampler.NullAdaptiveSamplerConfig
import com.twitter.zipkin.config.zookeeper.ZooKeeperConfig
import com.twitter.zipkin.config.{ZipkinQueryConfig, WriteQueueConfig, ScribeZipkinCollectorConfig}

object Configs {
  def collector(cassandraPort: Int) = new ScribeZipkinCollectorConfig {

    serverPort = 9410
    adminPort  = 9900

    adminStatsNodes =
      StatsFactory(
        reporters = JsonStatsLoggerFactory (
          loggerName = "stats",
          serviceName = Some("zipkin-collector")
        ) :: new TimeSeriesCollectorFactory
      )

    def writeQueueConfig = new WriteQueueConfig[T] {
      writeQueueMaxSize = 500
      flusherPoolSize = 10
    }

    var keyspaceBuilder = Keyspace.static(port = cassandraPort)

    def storageConfig = StorageBuilder(keyspaceBuilder)
    def indexConfig = IndexBuilder(keyspaceBuilder)
    def aggregatesConfig = AggregatesBuilder(keyspaceBuilder)

    override def adaptiveSamplerConfig = new NullAdaptiveSamplerConfig {}

    // sample it all
    override def globalSampler: GlobalSampler = EverythingGlobalSampler

    def zkConfig = new ZooKeeperConfig {
      servers = List("localhost:2181")
    }

    loggers =
      LoggerFactory (
        level = Level.DEBUG,
        handlers =
          new FileHandlerConfig {
            filename = "zipkin-collector.log"
            roll = Policy.SigHup
          } ::
            new ConsoleHandlerConfig() :: Nil
      ) :: LoggerFactory (
        node = "stats",
        level = Level.INFO,
        useParents = false,
        handlers =
          new FileHandlerConfig {
            filename = "stats.log"
            formatter = BareFormatterConfig
          }
      )
  }

  def query(cassandraPort: Int) = new ZipkinQueryConfig {
    serverPort = 9411
    adminPort  = 9901

    adminStatsNodes =
      StatsFactory(
        reporters = JsonStatsLoggerFactory(
          loggerName = "stats",
          serviceName = "zipkin-query"
        ) :: new TimeSeriesCollectorFactory
      )

    val keyspaceBuilder = Keyspace.static(port = cassandraPort)

    def storageConfig = StorageBuilder(keyspaceBuilder)
    def indexConfig = IndexBuilder(keyspaceBuilder)
    def aggregatesConfig = AggregatesBuilder(keyspaceBuilder)

    def zkConfig = new ZooKeeperConfig {
      servers = List("localhost:2181")
    }

    loggers =
      LoggerFactory (
        level = Level.DEBUG,
        handlers =
          new FileHandlerConfig {
            filename = "zipkin-query.log"
            roll = Policy.SigHup
          } ::
            new ConsoleHandlerConfig
      ) :: LoggerFactory (
        node = "stats",
        level = Level.INFO,
        useParents = false,
        handlers = new FileHandlerConfig {
          filename = "stats.log"
          formatter = BareFormatterConfig
        }
      )
  }
}
