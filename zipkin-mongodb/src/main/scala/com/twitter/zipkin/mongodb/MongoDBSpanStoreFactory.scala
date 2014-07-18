package com.twitter.zipkin.mongodb

import java.util.concurrent.TimeUnit

import com.twitter.app.App
import com.twitter.util.Duration
import com.twitter.zipkin.storage.SpanStore
import com.twitter.zipkin.storage.mongodb.MongoDBSpanStore

trait MongoDBSpanStoreFactory { self: App =>
  val mongodbURL = flag("zipkin.storage.mongodb.url", "mongodb://localhost:27017/", "MongoDB URL")
  val mongodbDatabase = flag("zipkin.storage.mongodb.database", "zipkin", "MongoDB Database")
  val mongodbTTL = flag("zipkin.storage.mongodb.spanTTL", 604800, "length of time MongoDB should store spans (in seconds)")

  def newMongoDBSpanStore(): SpanStore = new MongoDBSpanStore(mongodbURL(), mongodbDatabase(), Duration(mongodbTTL(), TimeUnit.SECONDS))
}