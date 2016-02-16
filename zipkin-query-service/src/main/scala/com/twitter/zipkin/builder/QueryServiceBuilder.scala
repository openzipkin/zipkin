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

import com.twitter.finagle.tracing.{DefaultTracer, NullTracer}
import com.twitter.finagle.zipkin.thrift.{RawZipkinTracer, SpanStoreZipkinTracer}
import com.twitter.zipkin.query.{BootstrapTrace, ZipkinQueryServer}
import com.twitter.zipkin.storage.{DependencyStore, NullDependencyStore, SpanStore}

case class QueryServiceBuilder(override val defaultFinatraHttpPort: String = "0.0.0.0:9411",
                               override val defaultHttpPort: Int = 9901,
                               logLevel: String = "INFO",
                               spanStore: SpanStore,
                               dependencies: DependencyStore = new NullDependencyStore,
                               override val defaultHttpServerName: String = "zipkin-query"
                                ) extends ZipkinQueryServer(spanStore, dependencies) {

  /** If the span transport is set, trace accordingly, or disable tracing. */
  premain {
    DefaultTracer.self = sys.env.get("TRANSPORT_TYPE") match {
      case Some("scribe") => RawZipkinTracer(sys.env.get("SCRIBE_HOST").getOrElse("localhost"), sys.env.get("SCRIBE_PORT").getOrElse("1463").toInt)
      case Some("http") => new SpanStoreZipkinTracer(spanStore, statsReceiver)
      case _ => NullTracer
    }
  }

  override def warmup() {
    super.warmup()
    BootstrapTrace.record("warmup")
  }

  override def postWarmup() {
    super.postWarmup()
    BootstrapTrace.complete()
  }
}
