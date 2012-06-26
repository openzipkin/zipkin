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
package com.twitter.zipkin.config

import com.twitter.zipkin.storage.{Aggregates, Index, Storage}
import com.twitter.zipkin.collector.{WriteQueue, ZipkinCollector}
import com.twitter.zipkin.collector.filter.{DefaultClientIndexingFilter, IndexingFilter}
import com.twitter.zipkin.collector.sampler.{AdaptiveSampler, ZooKeeperGlobalSampler, GlobalSampler}
import com.twitter.zipkin.config.collector.CollectorServerConfig
import com.twitter.zipkin.config.sampler._
import com.twitter.zipkin.config.zookeeper.{ZooKeeperClientConfig, ZooKeeperConfig}
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time._
import com.twitter.ostrich.admin.{ServiceTracker, RuntimeEnvironment}
import com.twitter.util.{FuturePool, Config}
import com.twitter.zk._
import java.net.{InetAddress, InetSocketAddress}
import org.apache.zookeeper.ZooDefs.Ids
import scala.collection.JavaConverters._
import scala.collection.Set
import com.twitter.zipkin.collector.processor._
import com.twitter.zipkin.common.Span

trait ZipkinCollectorConfig extends ZipkinConfig[ZipkinCollector] {

  var serverPort : Int = 9410
  var adminPort  : Int = 9900

  /* ZooKeeper paths */
  var zkConfigPath            : String = "/twitter/service/zipkin/config"
  var zkServerSetPath         : String = "/twitter/service/zipkin/collector"
  var zkScribePaths           : Set[String] = Set("/twitter/scribe/zipkin")

  /* ZooKeeper key for `AdjustableRateConfig`s */
  var zkSampleRateKey         : String = "samplerate"
  var zkStorageRequestRateKey : String = "storagerequestrate"

  /* Prefix for service/endpoint stats */
  var serviceStatsPrefix : String = "agg."

  /* Do not publish .p<percent> stats */
  adminStatsFilters = (serviceStatsPrefix + """.*\.p([0-9]*)""").r :: adminStatsFilters

  /* Storage */
  def storageConfig: StorageConfig
  lazy val storage: Storage = storageConfig.apply()

  /* Index */
  def indexConfig: IndexConfig
  lazy val index: Index = indexConfig.apply()

  /* Aggregates */
  def aggregatesConfig: AggregatesConfig
  lazy val aggregates: Aggregates = aggregatesConfig.apply()

  /* ZooKeeper */
  def zkConfig: ZooKeeperConfig

  def zkClientConfig: ZooKeeperClientConfig = new ZooKeeperClientConfig {
    var config = zkConfig
  }
  lazy val zkClient: ZooKeeperClient = zkClientConfig.apply()

  lazy val connector: Connector =
    CommonConnector(zkClient)(FuturePool.defaultPool)

  lazy val zClient: ZkClient =
    ZkClient(connector)
      .withAcl(Ids.OPEN_ACL_UNSAFE.asScala)
      .withRetryPolicy(RetryPolicy.Exponential(1.second, 1.5)(timer))

  /* `AdjustableRateConfig`s */
  lazy val sampleRateConfig: AdjustableRateConfig =
    ZooKeeperSampleRateConfig(zClient, zkConfigPath, zkSampleRateKey)
  lazy val storageRequestRateConfig: AdjustableRateConfig =
    ZooKeeperStorageRequestRateConfig(zClient, zkConfigPath, zkStorageRequestRateKey)

  /**
   *  Adaptive Sampler
   *  Dynamically adjusts the sample rate so we have a stable write throughput
   *  Default is a NullAdaptiveSamplerConfig that does nothing
   **/
  def adaptiveSamplerConfig: AdaptiveSamplerConfig = new NullAdaptiveSamplerConfig {}
  lazy val adaptiveSampler: AdaptiveSampler = adaptiveSamplerConfig.apply()

  def globalSampler: GlobalSampler = new ZooKeeperGlobalSampler(sampleRateConfig)

  /**
   * To accommodate a particular input type `T`, define a `rawDataFilter` that
   * converts the input data type (ex: Scrooge-generated Thrift) into a
   * sequence of `com.twitter.zipkin.common.Span`s
   */
  type T
  def rawDataFilter: ProcessorFilter[T, Seq[Span]]

  lazy val processor: Processor[T] =
    rawDataFilter andThen
    new SamplerProcessorFilter(globalSampler) andThen
    new SequenceProcessor[Span](
      new FanoutProcessor[Span]({
        new StorageProcessor(storage) ::
        new IndexProcessor(index, indexingFilter)
      })
    )

  def writeQueueConfig: WriteQueueConfig[T]
  lazy val writeQueue: WriteQueue[T] = writeQueueConfig.apply(processor)

  lazy val indexingFilter: IndexingFilter = new DefaultClientIndexingFilter

  lazy val serverAddr = new InetSocketAddress(InetAddress.getLocalHost, serverPort)

  val serverConfig: CollectorServerConfig

  def apply(runtime: RuntimeEnvironment): ZipkinCollector = {
    new ZipkinCollector(this)
  }
}

trait WriteQueueConfig[T] extends Config[WriteQueue[T]] {

  var writeQueueMaxSize: Int = 500
  var flusherPoolSize: Int = 10

  def apply(processor: Processor[T]): WriteQueue[T] = {
    val wq = new WriteQueue[T](writeQueueMaxSize, flusherPoolSize, processor)
    wq.start()
    ServiceTracker.register(wq)
    wq
  }

  def apply(): WriteQueue[T] = {
    val wq = new WriteQueue[T](writeQueueMaxSize, flusherPoolSize, new NullProcessor[T])
    wq.start()
    ServiceTracker.register(wq)
    wq
  }
}
