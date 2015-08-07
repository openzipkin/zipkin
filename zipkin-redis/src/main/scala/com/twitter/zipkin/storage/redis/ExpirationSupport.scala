package com.twitter.zipkin.storage.redis

import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import org.jboss.netty.buffer.ChannelBuffer

trait ExpirationSupport {
  val client: Client

  /** Expires keys older than this many seconds. */
  val defaultTtl: Option[Duration]

  def expireOnTtl(redisKey: ChannelBuffer, ttl: Option[Duration] = defaultTtl): Future[Unit] = {
    if (ttl.isDefined) client.expire(redisKey, ttl.get.inLongSeconds).unit else Future.Unit
  }
}
