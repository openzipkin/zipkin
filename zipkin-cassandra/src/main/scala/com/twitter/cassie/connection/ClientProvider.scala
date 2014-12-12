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

import com.twitter.util.Future
import org.apache.cassandra.finagle.thrift.Cassandra.ServiceToClient

/**
 * A utility interface for classes which pass a Cassandra `Client` instance to
 * a function and return the result.
 */
trait ClientProvider {
  /**
   * Passes a Cassandra `ServiceToClient` instance to the given function and returns a
   * future which will be the client's response.
   *
   * @tparam A the result type
   * @param f the function to which the `ServiceToClient` is passed
   * @return `f(client)`
   */
  def map[A](f: ServiceToClient => Future[A]): Future[A]

  /**
   * Releases any resources held by the provider.
   */
  def close() = {}
}
