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
package com.twitter.zipkin.builder

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.{ThriftClientRequest, ThriftClientFramedCodec}
import java.net.InetSocketAddress
import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster

/**
 * Convenience functions for creating ZipkinQuery client builders
 */
object QueryClient {

  private def default = ClientBuilder()
    .name("ZipkinQuery")
    .codec(ThriftClientFramedCodec())
    .hostConnectionLimit(1)

  /* Query client builder for a static host */
  def static(address: InetSocketAddress) = new Builder[ClientBuilder.Complete[ThriftClientRequest, Array[Byte]]] {
    def apply() = default.hosts(address)
  }

  /* Query client builder for a zookeeper serverset based cluster */
  def zookeeper(
    zkClientBuilder: ZooKeeperClientBuilder,
    serverSetPath: String
  ) = new Builder[ClientBuilder.Complete[ThriftClientRequest, Array[Byte]]] {
    def apply() = {
      val serverSet = new ServerSetImpl(zkClientBuilder.apply(), serverSetPath)
      val cluster = new ZookeeperServerSetCluster(serverSet) {
        override def ready() = super.ready
      }
      default.cluster(cluster)
    }
  }
}
