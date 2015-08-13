package com.twitter.zipkin.redis

import com.twitter.conversions.time._
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.util.{Await, Duration}
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.SpanStore
import com.twitter.zipkin.storage.redis.RedisSpanStore

/** Allows [[RedisSpanStore]] to be used with legacy [[Builder]]s. */
case class SpanStoreBuilder(
  client: Client,
  ttl: Option[Duration] = Some(7.days),
  authPassword: Option[String] = None
) extends Builder[SpanStore] {self =>

  def ttl(t: Duration): SpanStoreBuilder = copy(ttl = Some(t))

  def apply() = {
    if (authPassword.isDefined) {
      Await.result(client.auth(StringToChannelBuffer(authPassword.get)))
    }
    new RedisSpanStore(client, self.ttl)
  }
}
