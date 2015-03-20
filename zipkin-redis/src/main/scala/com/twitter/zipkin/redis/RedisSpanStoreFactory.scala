package com.twitter.zipkin.redis

import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.zipkin.storage.redis.RedisSpanStore

/**
 * Created by caporp01 on 19/03/2015.
 */
trait RedisSpanStoreFactory { self: App =>
  val redisHost = flag("zipkin.storage.redis.host", "0.0.0.0", "Host for Redis")
  val redisPort = flag("zipkin.storage.redis.port", 6379, "Port for Redis")
  val redisTtl = flag("zipkin.storage.redis.ttl", 168, "Redis data TTL in hours")

  def newRedisSpanStore(): RedisSpanStore = {
    val storage = StorageBuilder(redisHost(), redisPort(), redisTtl().hours)
    val index = IndexBuilder(redisHost(), redisPort(), redisTtl().hours)
    new RedisSpanStore(index.apply(), storage.apply())
  }
}
