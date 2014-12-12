// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.connection

import com.twitter.finagle.builder.{ StaticCluster => FStaticCluster, Cluster => FCluster }
import com.twitter.finagle.ServiceFactory
import java.net.SocketAddress

trait CCluster[T] extends FCluster[T] {
  def close
}

/**
 * A cassandra cluster specified by socket addresses. No remapping.
 */
class SocketAddressCluster(private[this] val underlying: Seq[SocketAddress])
  extends FStaticCluster[SocketAddress](underlying) with CCluster[SocketAddress] {
  def close() = ()
}
