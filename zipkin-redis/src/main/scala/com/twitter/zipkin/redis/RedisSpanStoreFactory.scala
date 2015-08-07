package com.twitter.zipkin.redis

import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.util.Duration
import com.twitter.zipkin.storage.redis.RedisSpanStore

trait RedisSpanStoreFactory { self: App =>
  // TODO: authPassword!
  val redisHost = flag("zipkin.storage.redis.host", "0.0.0.0", "Host for Redis")
  val redisPort = flag("zipkin.storage.redis.port", 6379, "Port for Redis")
  val redisTtl = flag("zipkin.storage.redis.ttl", 168, "Redis data TTL in hours. A value smaller or equal to 0 means no ttl")
  val connectionCoreSize = flag("zipkin.storage.redis.connectionCoreSize", 20, "The core size of the connection pool")
  val connectionLimit = flag("zipkin.storage.redis.connectionLimit", 40, "The maximum number of connections that are allowed per host")

  def newRedisSpanStore(): RedisSpanStore = {

    val client = Client(ClientBuilder().hosts("%s:%d".format(redisHost(), redisPort()))
                                       .hostConnectionLimit(connectionLimit())
                                       .hostConnectionCoresize(connectionCoreSize())
                                       .codec(Redis())
                                       .daemon(true)
                                       .build())

    var optionalTtl:Option[Duration] = null
    if (redisTtl() <= 0) {
      optionalTtl = None
    } else {
      optionalTtl = Some(redisTtl().hours)
    }
    new RedisSpanStore(client, optionalTtl)
  }
}
