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

import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.zipkin.collector.ScribeCollectorService
import com.twitter.zipkin.gen
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.zipkin.config.collector.CollectorServerConfig

class ScribeCollectorServerConfig(config: ScribeZipkinCollectorConfig) extends CollectorServerConfig {

  val log = Logger.get(Logger.getClass)

  def apply(): Server = {
    log.info("Starting collector service on addr " + config.serverAddr)

    /* Start the service */
    val service = new ScribeCollectorService(config, config.writeQueue, config.categories)
    service.start()
    ServiceTracker.register(service)

    /* Start the server */
    ServerBuilder()
      .codec(ThriftServerFramedCodec())
      .bindTo(config.serverAddr)
      .name("ZipkinCollector")
      .reportTo(config.statsReceiver)
      .build(new gen.ZipkinCollector.FinagledService(service, new TBinaryProtocol.Factory()))
  }
}
