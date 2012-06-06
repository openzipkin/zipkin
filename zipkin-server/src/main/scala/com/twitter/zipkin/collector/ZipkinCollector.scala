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
package com.twitter.zipkin.collector

import com.twitter.finagle.builder.Server
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.zipkin.config.ZipkinCollectorConfig

class ZipkinCollector(config: ZipkinCollectorConfig) extends Service {

  val log = Logger.get(getClass.getName)
  var thriftServer: Server = null

  def start() {
    thriftServer = config.serverConfig()

    // Start up adaptive sampler
    config.adaptiveSampler.start()
    ServiceTracker.register(config.adaptiveSampler)
  }

  def shutdown() {
    log.info("Shutting down collector thrift service.")

    thriftServer.close()
    ServiceTracker.shutdown()
  }
}


