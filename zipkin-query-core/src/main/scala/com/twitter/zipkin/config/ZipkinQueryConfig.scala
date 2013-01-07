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

import com.twitter.zipkin.query.ZipkinQuery
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.Store
import com.twitter.common.zookeeper.{ServerSetImpl, ZooKeeperClient}
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.query.adjusters.{NullAdjuster, TimeSkewAdjuster, Adjuster}
import com.twitter.zipkin.config.zookeeper.{ZooKeeperClientConfig, ZooKeeperConfig}
import com.twitter.zipkin.builder.Builder

trait ZipkinQueryConfig extends ZipkinConfig[ZipkinQuery] {

  var serverPort : Int = 9411
  var adminPort  : Int = 9901

  var serverSetPath: String = "/twitter/service/zipkin/query"

  var adjusterMap: Map[gen.Adjust, Adjuster] = Map (
    gen.Adjust.Nothing -> NullAdjuster,
    gen.Adjust.TimeSkew -> new TimeSkewAdjuster()
  )

  def storeBuilder: Builder[Store]
  lazy val store: Store = storeBuilder.apply()

  def zkConfig: ZooKeeperConfig

  def zkClientConfig = new ZooKeeperClientConfig {
    var config = zkConfig
  }
  lazy val zkClient: ZooKeeperClient = zkClientConfig.apply()

  lazy val serverSet: ServerSetImpl = new ServerSetImpl(zkClient, serverSetPath)

  override lazy val tracerFactory =
    ZipkinTracer(statsReceiver = statsReceiver, sampleRate = 1f)

  def apply(runtime: RuntimeEnvironment) = {
    new ZipkinQuery(this, serverSet, store.storage, store.index, store.aggregates)
  }
}
