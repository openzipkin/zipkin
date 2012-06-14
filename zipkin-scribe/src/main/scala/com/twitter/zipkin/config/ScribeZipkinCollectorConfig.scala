package com.twitter.zipkin.config

import com.twitter.zipkin.config.collector.CollectorServerConfig

trait ScribeZipkinCollectorConfig extends ZipkinCollectorConfig {
  val serverConfig: CollectorServerConfig = new ScribeCollectorServerConfig(this)
}
