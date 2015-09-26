package com.twitter.zipkin.query

import com.google.inject.Stage
import com.twitter.finatra.http.test.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import com.twitter.zipkin.storage.{InMemorySpanStore, NullDependencyStore}

class ZipkinQueryStartupTest extends FeatureTest {

  override val server = new EmbeddedHttpServer(
    twitterServer = new ZipkinQueryServer(new InMemorySpanStore, new NullDependencyStore),
    stage = Stage.PRODUCTION,
    verbose = false)

  "Server" should {
    "startup" in {
      server.assertHealthy()
    }
  }
}