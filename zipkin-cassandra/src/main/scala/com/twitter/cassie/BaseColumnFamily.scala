// Copyright 2012 Twitter, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie

import com.twitter.cassie.codecs.ThriftCodec
import com.twitter.cassie.connection.ClientProvider
import com.twitter.cassie.util.FutureUtil
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.tracing.Trace
import com.twitter.util.Future
import org.slf4j.Logger
import org.apache.cassandra.finagle.thrift
import org.apache.cassandra.finagle.thrift.Cassandra.ServiceToClient

object BaseColumnFamily {
  val annPredCodec = new ThriftCodec[thrift.SlicePredicate](classOf[thrift.SlicePredicate])
}

private[cassie] abstract class BaseColumnFamily(keyspace: String, cf: String, provider: ClientProvider, stats: StatsReceiver) {

  import BaseColumnFamily._
  import FutureUtil._

  val baseAnnotations = Map("keyspace" -> keyspace, "columnfamily" -> cf)

  protected def trace(annotations: Map[String, Any]) {
    Trace.recordBinaries(baseAnnotations)
    Trace.recordBinaries(annotations)
  }

  /**
   * @param name The thrift method name being called
   * @param traceAnnotations Optional annotations for tracing
   * @param args A lazily constructed list of the arguments to the thrift method, for debug logging
   * @param f Function ot receives the connection
   */
  def withConnection[T](
    name: String,
    traceAnnotations: Map[String, Any] = Map.empty,
    args: => Seq[Any] = Nil
  )(f: ServiceToClient => Future[T]
  )(implicit log: Logger): Future[T] = {
    if (log.isDebugEnabled) {
      log.debug(args.mkString(name + "(", ", ", ")"))
    }
    timeFutureWithFailures(stats, name) {
      // Associate trace annotations with the client span by using a terminal trace id
      Trace.unwind {
        Trace.setTerminalId(Trace.nextId)

        trace(traceAnnotations)
        provider.map(f)
      }
    }
  }
}
