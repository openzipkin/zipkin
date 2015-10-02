package com.twitter.zipkin.storage.redis

import com.twitter.app.App
import com.twitter.util.Await.ready
import com.twitter.zipkin.redis.RedisSpanStoreFactory
import com.twitter.zipkin.storage.SpanStoreSpec

class RedisSpanStoreSpec extends SpanStoreSpec {
  object RedisStore extends App with RedisSpanStoreFactory
  RedisStore.main(Array(
    "-zipkin.storage.redis.host", "127.0.0.1",
    "-zipkin.storage.redis.port", "6379",
    "-zipkin.storage.redis.ttl", "0"))

  val store = RedisStore.newRedisSpanStore()

  override def clear = {
    ready(store.clear())
  }
}
