package com.twitter.zipkin.builder

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.{ThriftClientRequest, ThriftClientFramedCodec}
import java.net.InetSocketAddress
import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster

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
      val a = default.cluster(cluster)
      a
    }
  }
}
