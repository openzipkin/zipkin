/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.config

import com.twitter.zipkin.collector.processor.ScribeFilter
import com.twitter.zipkin.config.collector.CollectorServerConfig

trait ScribeZipkinCollectorConfig extends ZipkinCollectorConfig {
  type T = Seq[String]
  val serverConfig: CollectorServerConfig = new ScribeCollectorServerConfig(this)

  var zkScribePaths: Set[String] = Set("/twitter/scribe/zipkin")

  /* Categories of incoming scribe messages. Let these through, drop any others */
  var categories: Set[String] = Set("zipkin")

  def rawDataFilter = new ScribeFilter
}
