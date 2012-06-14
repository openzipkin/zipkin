/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.twitter.zipkin.config._
import com.twitter.zipkin.config.sampler.NullAdaptiveSamplerConfig
import com.twitter.zipkin.config.zookeeper.ZooKeeperConfig
import com.twitter.conversions.time._
import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.ostrich.admin.{TimeSeriesCollectorFactory, JsonStatsLoggerFactory, StatsFactory}

// development mode.
new ScribeZipkinCollectorConfig {

  serverPort = 9410
  adminPort  = 9900

  adminStatsNodes =
    StatsFactory(
      reporters = JsonStatsLoggerFactory (
        loggerName = "stats",
        serviceName = Some("zipkin-collector")
      ) :: new TimeSeriesCollectorFactory
    )

  def writeQueueConfig = new WriteQueueConfig {
    writeQueueMaxSize = 500
    flusherPoolSize = 10
  }

  val _cassandraConfig = new CassandraConfig {
    useServerSets = false
    mapHosts = false
    tracesTimeToLive = 3.days
  }

  def storageConfig = new CassandraStorageConfig {
    def cassandraConfig = _cassandraConfig
  }

  def indexConfig = new CassandraIndexConfig {
    def cassandraConfig = _cassandraConfig
  }

  override def adaptiveSamplerConfig = new NullAdaptiveSamplerConfig {}

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
